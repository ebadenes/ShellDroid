package io.shelldroid.feature.terminal

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.ui.graphics.Color
import io.shelldroid.core.ssh.ShellChannel
import io.shelldroid.core.ssh.SshSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory

/**
 * Glue between libssh's [ShellChannel] and ConnectBot's
 * [TerminalEmulator]. Modelled after `TerminalBridge` in ConnectBot:
 *
 *   - Owns a [scope] that survives the [TerminalViewModel] (SupervisorJob
 *     on Dispatchers.IO), so cleanup on back/destroy never blocks main.
 *   - Emulator is created immediately on [attach] with the provided skin
 *     colors. User keystrokes from Termlib are pushed into a
 *     [Channel<ByteArray>] which is drained by a writer coroutine as
 *     soon as the SSH [ShellChannel] is ready.
 *   - A reader coroutine polls `readStdoutNonblocking` with an adaptive
 *     0 -> 50 ms backoff (same pattern JuiceSSH uses in its SSH streams)
 *     and posts the bytes to `emulator.writeInput` via the main Handler.
 *   - `onResize` from the emulator is forwarded to the remote pty.
 *
 * Not Hilt-injected: one instance per [TerminalViewModel].
 */
class TerminalBridge(
    private val sessionManager: SshSessionManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val writeChannel = Channel<ByteArray>(capacity = Channel.UNLIMITED)

    private val _emulator = MutableStateFlow<TerminalEmulator?>(null)
    val emulator: StateFlow<TerminalEmulator?> = _emulator.asStateFlow()

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    sealed interface State {
        data object Idle : State
        data object Connecting : State
        data object Running : State
        data class Error(val message: String) : State
        data object Closed : State
    }

    @Volatile private var shellChannel: ShellChannel? = null
    @Volatile private var hostIdCache: String? = null
    private var readerJob: Job? = null
    private var writerJob: Job? = null

    /**
     * Attach this bridge to the warm SSH session for [hostId]. Creates the
     * emulator synchronously on the caller thread (typically main) and
     * kicks off the channel open + reader + writer on IO. Returns `true`
     * iff a warm session exists; `false` means the caller forgot to
     * connect via [SshSessionManager] first.
     */
    fun attach(
        hostId: String,
        initialCols: Int,
        initialRows: Int,
        defaultForeground: Int,
        defaultBackground: Int,
    ): Boolean {
        if (_emulator.value != null) return true // already attached
        val client = sessionManager.getClient(hostId)
        if (client == null) {
            _state.value = State.Error("no warm SSH session for $hostId")
            return false
        }
        hostIdCache = hostId
        _state.value = State.Connecting

        val em = TerminalEmulatorFactory.create(
            initialRows = initialRows,
            initialCols = initialCols,
            defaultForeground = Color(defaultForeground),
            defaultBackground = Color(defaultBackground),
            onKeyboardInput = { data ->
                // Called by Termlib on every user keystroke (IME + physical).
                // Enqueue to the writer; the coroutine drains on IO and
                // calls ShellChannel.write under the session mutex.
                writeChannel.trySend(data)
            },
            onBell = { /* TODO: haptic or sound */ },
            onResize = { dims ->
                // Termlib reflowed the grid; tell the remote pty.
                scope.launch {
                    try {
                        shellChannel?.resize(dims.columns, dims.rows)
                    } catch (_: Throwable) {
                        // channel might be closed; ignore
                    }
                }
            },
            onClipboardCopy = { /* TODO: wire to ClipboardManager */ },
            onProgressChange = { _, _ -> /* OSC 9;4 — TODO */ },
        )
        _emulator.value = em

        scope.launch {
            val ch: ShellChannel = try {
                client.openShell(initialCols, initialRows)
            } catch (t: Throwable) {
                Log.e(TAG, "openShell failed", t)
                _state.value = State.Error("openShell: ${t.message}")
                return@launch
            }
            shellChannel = ch
            _state.value = State.Running
            startReader(ch, em)
            startWriter(ch)
        }
        return true
    }

    private fun startReader(ch: ShellChannel, em: TerminalEmulator) {
        readerJob = scope.launch {
            val buf = ByteArray(4096)
            var backoffMs = 0
            while (isActive) {
                val n = try {
                    ch.readStdoutNonblocking(buf)
                } catch (t: Throwable) {
                    Log.e(TAG, "reader error", t)
                    break
                }
                when {
                    n > 0 -> {
                        backoffMs = 0
                        val copy = buf.copyOf(n)
                        mainHandler.post {
                            try {
                                em.writeInput(copy, 0, n)
                            } catch (_: Throwable) {
                                // emulator might be torn down; ignore
                            }
                        }
                    }
                    n == 0 -> {
                        if (ch.isEof()) {
                            Log.d(TAG, "reader: EOF")
                            break
                        }
                        if (backoffMs < 50) backoffMs += 1
                        delay(backoffMs.toLong())
                    }
                    else -> {
                        Log.d(TAG, "reader: read=$n, exiting")
                        break
                    }
                }
            }
            _state.value = State.Closed
        }
    }

    private fun startWriter(ch: ShellChannel) {
        writerJob = scope.launch {
            for (data in writeChannel) {
                try {
                    ch.write(data, 0, data.size)
                } catch (_: Throwable) {
                    // channel closed; drop remaining
                    break
                }
            }
        }
    }

    /**
     * Tear down the shell channel and free the emulator. If
     * [disconnectSession] is true, also fully disconnects the libssh
     * session via [SshSessionManager]; otherwise the session stays warm
     * so the user can reconnect instantly.
     *
     * All teardown runs on the bridge's own IO scope, never blocking
     * the caller thread.
     */
    fun detach(disconnectSession: Boolean) {
        val host = hostIdCache
        hostIdCache = null
        // Snapshot jobs/channel BEFORE launching, so the cleanup coroutine
        // can operate on stable references even if a concurrent reattach
        // mutates the fields.
        val reader = readerJob
        val writer = writerJob
        val ch = shellChannel
        readerJob = null
        writerJob = null
        shellChannel = null
        scope.launch {
            try {
                // CRITICAL: wait for the reader AND writer to actually
                // exit their native calls before closing the channel.
                // Without this, ch.close() races with a ssh_channel_read
                // / ssh_channel_write in progress and corrupts libssh's
                // per-session cipher state — manifesting as
                // "Packet len too high" on the NEXT ssh_channel_open_session
                // the user triggers (i.e. the "Mantener" back then
                // reconnect flow).
                writeChannel.close()
                try { reader?.cancelAndJoin() } catch (_: Throwable) {}
                try { writer?.cancelAndJoin() } catch (_: Throwable) {}
                try { ch?.close() } catch (_: Throwable) {}
                _emulator.value = null
                if (disconnectSession && host != null) {
                    sessionManager.disconnect(host)
                }
            } finally {
                // kill the scope last so the in-flight teardown above
                // gets to complete
                scope.cancel()
            }
        }
    }

    companion object {
        private const val TAG = "TerminalBridge"
    }
}
