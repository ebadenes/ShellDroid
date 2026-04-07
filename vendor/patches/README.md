# Vendor patches

Local modifications applied on top of vendored upstream sources. Each patch is
small and surgical to minimize merge conflicts when bumping upstream versions.

## `termux-terminal-session.patch`

**Target:** `vendor/termux-app/terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java`
**Pinned upstream tag:** `v0.118.3` (see `vendor/setup.sh`)

### Why
`TerminalSession` is designed for **local PTY** sessions (forks a subprocess via
`JNI.createSubprocess`). ShellDroid drives the terminal from a **remote SSH
channel** via libssh, so we need a headless variant that:

1. Skips the `fork+exec` in `initializeEmulator()`
2. Routes `write(byte[],int,int)` to a libssh `ssh_channel_write` instead of
   the local PTY file descriptor
3. Allows external code to feed received bytes into `mEmulator.append(...)`

### What the patch does
Four surgical changes:

1. Removes the `final` modifier from `public final class TerminalSession`
   → allows subclassing
2. Promotes `mEmulator` from package-private to `protected`
   → subclass can read/replace it
3. Promotes `mClient` from package-private to `protected`
4. Promotes `mShellPid` from package-private to `protected`

### What we do NOT patch
- `TerminalView.java` is **not** patched. The existing `attachSession()` API
  works fine because our `SshTerminalSession` (subclass) IS-A `TerminalSession`.
- `JNI` class is untouched.
- `terminal-view` module is untouched.

### How the subclass works (lives in `:feature:terminal`)
```kotlin
class SshTerminalSession(
    columns: Int, rows: Int, cellW: Int, cellH: Int,
    client: TerminalSessionClient,
    private val channel: ShellChannel,
    private val mainHandler: Handler,
) : TerminalSession("", "", emptyArray(), emptyArray(), null, client) {

    override fun initializeEmulator(columns: Int, rows: Int, cellW: Int, cellH: Int) {
        // Headless: build the emulator without forking a subprocess
        mEmulator = TerminalEmulator(this, columns, rows, cellW, cellH, null, mClient)
        mShellPid = 1  // pretend "alive" so TerminalView treats us as active
        // Do NOT call JNI.createSubprocess
        // Do NOT spawn TermSessionInputReader/OutputWriter/Waiter
    }

    override fun write(data: ByteArray, offset: Int, count: Int) {
        // Send user input to the remote shell
        channel.writeAsync(data, offset, count)
    }

    /** Called by the libssh reader coroutine on the main thread. */
    fun feedFromShell(buf: ByteArray, length: Int) {
        mainHandler.post {
            mEmulator?.append(buf, length)
            notifyScreenUpdate()
        }
    }
}
```

### Risk
If Termux upstream renames or restructures `TerminalSession`'s field declarations,
this patch will fail to apply. Mitigation: pin a specific upstream tag in
`vendor/setup.sh` and bump deliberately.
