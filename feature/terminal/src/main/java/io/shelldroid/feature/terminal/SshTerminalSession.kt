package io.shelldroid.feature.terminal

import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A [TerminalSession] whose I/O is backed by a remote SSH shell channel
 * instead of a local pty'd child process.
 *
 * The parent ctor only stores fields (verified in the vendored Termux source),
 * so passing nulls for shell path / args / env is safe — we never let the
 * parent try to spawn anything. We override [initializeEmulator] to construct
 * the emulator directly and mark [mShellPid] as alive so [isRunning] reports
 * `true`.
 *
 * Main-thread dispatching of emulator updates is parameterised via
 * [mainExecutor] so tests can pass a synchronous runner and avoid pulling in
 * `android.os.Handler`/`Looper` under pure JVM.
 */
class SshTerminalSession(
    private val io: TerminalIo,
    private val ioScope: CoroutineScope,
    private val mainExecutor: (Runnable) -> Unit,
    client: TerminalSessionClient,
) : TerminalSession(null, null, null, null, null, client) {

    override fun initializeEmulator(cols: Int, rows: Int, cellW: Int, cellH: Int) {
        mEmulator = TerminalEmulator(this, cols, rows, cellW, cellH, null, mClient)
        // Pseudo-alive pid so parent.isRunning() returns true. -1 == finished.
        mShellPid = 1
    }

    /**
     * Override the parent's [updateSize] to avoid its `JNI.setPtyWindowSize`
     * call, which triggers loading of `libtermux.so` (we exclude it from the
     * APK and never spawn a local pty anyway). We resize the emulator in
     * place and forward the new window dimensions to the remote via the
     * shell channel.
     */
    override fun updateSize(cols: Int, rows: Int, cellW: Int, cellH: Int) {
        val em = mEmulator
        if (em == null) {
            initializeEmulator(cols, rows, cellW, cellH)
            return
        }
        em.resize(cols, rows, cellW, cellH)
        // io.resize is suspend and acquires the session mutex via
        // ShellChannel. Launch from ioScope so we don't block whoever
        // called updateSize (usually TerminalView layout pass on main).
        ioScope.launch {
            try {
                io.resize(cols, rows)
            } catch (_: Throwable) {
                // channel may be closed mid-resize — ignore
            }
        }
    }

    override fun write(data: ByteArray?, offset: Int, count: Int) {
        if (data == null || count <= 0) return
        val copy = data.copyOfRange(offset, offset + count)
        // io.write is already a suspend fn that does withContext(Dispatchers.IO)
        // internally for the native libssh call, so launching on ioScope's
        // default dispatcher (Main in prod, the test scheduler in tests) is
        // fine — the actual blocking work is dispatched by ShellChannel.
        ioScope.launch {
            try {
                io.write(copy, 0, copy.size)
            } catch (_: CancellationException) {
                // scope cancelled
            } catch (_: Throwable) {
                // channel may be closed mid-write — ignore
            }
        }
    }

    /**
     * Feed bytes received from the remote shell into the emulator.
     * Must end up on the main thread — dispatched via [mainExecutor].
     */
    fun feedFromShell(buf: ByteArray, length: Int) {
        if (length <= 0) return
        mainExecutor(Runnable {
            mEmulator?.append(buf, length)
            notifyScreenUpdate()
        })
    }

    /** No local child process to reap. */
    override fun finishIfRunning() {
        // no-op
    }

    /**
     * Close the underlying channel and mark the session finished. Suspend
     * because [io.close] acquires the session mutex and does a native
     * libssh round trip.
     */
    suspend fun closeChannel() {
        try {
            io.close()
        } catch (_: Throwable) {
            // ignore
        }
        mShellPid = -1
    }
}
