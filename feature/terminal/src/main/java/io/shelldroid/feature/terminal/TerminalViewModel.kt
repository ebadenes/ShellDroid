package io.shelldroid.feature.terminal

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.shelldroid.core.db.dao.HostDao
import io.shelldroid.core.ssh.ShellChannel
import io.shelldroid.core.ssh.SshSessionManager
import io.shelldroid.feature.terminal.skin.TerminalSkin
import io.shelldroid.feature.terminal.skin.TerminalSkinRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TerminalState {
    data object Idle : TerminalState
    data object Connecting : TerminalState
    data object Running : TerminalState
    data class Error(val message: String) : TerminalState
    data object Closed : TerminalState
}

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val sessionManager: SshSessionManager,
    private val hostDao: HostDao,
    skinRepository: TerminalSkinRepository,
) : ViewModel() {

    val skin: StateFlow<TerminalSkin> = skinRepository.selected
        .stateIn(viewModelScope, SharingStarted.Eagerly, skinRepository.current())

    private val _state = MutableStateFlow<TerminalState>(TerminalState.Idle)
    val state: StateFlow<TerminalState> = _state.asStateFlow()

    private val _session = MutableStateFlow<SshTerminalSession?>(null)
    val session: StateFlow<SshTerminalSession?> = _session.asStateFlow()

    private val _title = MutableStateFlow("Terminal")
    val title: StateFlow<String> = _title.asStateFlow()

    fun loadTitle(hostId: String) {
        viewModelScope.launch {
            val host = hostDao.findById(hostId) ?: return@launch
            _title.value = host.name.ifBlank { "${host.username}@${host.hostname}" }
        }
    }

    private var io: TerminalIo? = null
    private var channel: ShellChannel? = null
    private var readStdoutJob: Job? = null

    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }
    private val mainExecutor: (Runnable) -> Unit = { r -> mainHandler.post(r) }

    /**
     * Open a shell on the active session for [hostId] and begin pumping I/O.
     * Safe to call multiple times: subsequent invocations after a successful
     * start are ignored.
     */
    fun start(hostId: String, cols: Int, rows: Int, cellW: Int, cellH: Int) {
        Log.d(TAG, "start(host=$hostId, ${cols}x${rows} cell=${cellW}x${cellH}) sessionAlreadySet=${_session.value != null}")
        if (_session.value != null) return
        _state.value = TerminalState.Connecting

        val client = sessionManager.getClient(hostId)
        if (client == null) {
            Log.e(TAG, "getClient($hostId) returned null — no warm SSH session")
            _state.value = TerminalState.Error("no active session for host $hostId")
            return
        }
        Log.d(TAG, "got warm LibSshClient, opening shell")

        val ch: ShellChannel = try {
            client.openShell(cols, rows)
        } catch (t: Throwable) {
            Log.e(TAG, "openShell failed", t)
            _state.value = TerminalState.Error("openShell failed: ${t.message}")
            return
        }
        Log.d(TAG, "openShell ok, starting reader")
        channel = ch
        val termIo = ShellChannelTerminalIo(ch).also { io = it }

        val session = SshTerminalSession(
            io = termIo,
            ioScope = viewModelScope,
            mainExecutor = mainExecutor,
            client = NoOpTerminalSessionClient(),
        )
        session.initializeEmulator(cols, rows, cellW, cellH)
        _session.value = session
        _state.value = TerminalState.Running

        // With a PTY allocated, the remote tty merges stderr into stdout,
        // so we only need a single reader. Running two concurrent
        // ssh_channel_read calls (stdout + stderr) on the same session races
        // inside libssh's internal state machine and can corrupt the
        // channel so that subsequent ssh_channel_write calls return
        // SSH_ERROR — observed on-device as "io.write returned -1" while
        // output still rendered fine.
        readStdoutJob = viewModelScope.launch(Dispatchers.IO) {
            val buf = ByteArray(4096)
            var total = 0L
            // Polling read so that cancellation is cooperative. 200 ms is
            // short enough to feel snappy on back-out but long enough that
            // we are not burning CPU when idle. On timeout we just loop.
            while (isActive) {
                val n = try {
                    ch.readStdoutTimeout(buf, 200)
                } catch (t: Throwable) {
                    Log.e(TAG, "read error", t)
                    _state.value = TerminalState.Error("read error: ${t.message}")
                    break
                }
                if (n == 0) continue // timeout, keep polling
                if (n < 0) {
                    Log.d(TAG, "reader: read returned $n, total=$total, exiting")
                    break
                }
                total += n
                val copy = buf.copyOf(n)
                session.feedFromShell(copy, n)
            }
            _state.value = TerminalState.Closed
        }
    }

    /**
     * Forward a layout change to the local emulator (cheap, main-thread OK)
     * and to the remote PTY (JNI call into libssh, must NOT block main —
     * dispatched to IO).
     */
    fun resize(cols: Int, rows: Int, cellW: Int, cellH: Int) {
        _session.value?.emulator?.resize(cols, rows, cellW, cellH)
        val currentIo = io ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                currentIo.resize(cols, rows)
            } catch (_: Throwable) {
                // channel may have closed mid-resize
            }
        }
    }

    /**
     * Hard-disconnect the underlying SSH session for [hostId]. Unlike
     * [onCleared] (which only tears down the shell channel and leaves
     * the session warm in [SshSessionManager]), this fully disconnects
     * from the remote server. Used when the user explicitly chooses
     * "Disconnect" on the back dialog.
     */
    fun disconnectHost(hostId: String) {
        Log.d(TAG, "disconnectHost($hostId) — tearing down warm session")
        val job = readStdoutJob
        val session = _session.value
        _session.value = null
        // Same ordering discipline as onCleared: stop the reader, wait for
        // it to actually exit, then close the channel.
        runBlocking {
            withContext(NonCancellable) {
                try {
                    job?.cancel()
                    job?.join()
                } catch (_: Throwable) {
                    // ignore
                }
                session?.closeChannel()
            }
        }
        sessionManager.disconnect(hostId)
    }

    override fun onCleared() {
        Log.d(TAG, "onCleared — cancelling reader and closing channel")
        super.onCleared()
        val job = readStdoutJob
        val session = _session.value
        _session.value = null
        // CRITICAL: we MUST wait for the reader coroutine to actually exit
        // before closing the libssh channel, otherwise close races with an
        // in-flight ssh_channel_read and corrupts the session cipher state
        // (manifests as "Packet len too high" on the next
        // ssh_channel_open_session — i.e. the black-screen-on-reconnect bug).
        //
        // onCleared runs on the main thread after the nav pop, so the view
        // is already gone; a brief blocking wait here is visually invisible.
        runBlocking {
            withContext(NonCancellable) {
                try {
                    job?.cancel()
                    job?.join()
                } catch (_: Throwable) {
                    // ignore
                }
                session?.closeChannel()
            }
        }
    }

    companion object { private const val TAG = "TerminalVM" }
}
