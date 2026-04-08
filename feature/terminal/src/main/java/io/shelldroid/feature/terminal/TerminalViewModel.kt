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
import kotlinx.coroutines.withTimeoutOrNull
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
    @Volatile private var starting: Boolean = false

    /**
     * Open a shell on the active session for [hostId] and begin pumping
     * I/O. Idempotent: repeated calls while a session is already up (or
     * being set up) return immediately. The actual openShell call is
     * dispatched to IO so the main-thread layout listener does not block.
     */
    fun start(hostId: String, cols: Int, rows: Int, cellW: Int, cellH: Int) {
        Log.d(TAG, "start(host=$hostId, ${cols}x${rows} cell=${cellW}x${cellH}) sessionAlreadySet=${_session.value != null}")
        if (_session.value != null || starting) return
        starting = true
        _state.value = TerminalState.Connecting

        viewModelScope.launch {
            val client = sessionManager.getClient(hostId)
            if (client == null) {
                Log.e(TAG, "getClient($hostId) returned null — no warm SSH session")
                _state.value = TerminalState.Error("no active session for host $hostId")
                starting = false
                return@launch
            }
            Log.d(TAG, "got warm LibSshClient, opening shell")

            val ch: ShellChannel = try {
                client.openShell(cols, rows)
            } catch (t: Throwable) {
                Log.e(TAG, "openShell failed", t)
                _state.value = TerminalState.Error("openShell failed: ${t.message}")
                starting = false
                return@launch
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
            starting = false

            // Single reader: PTY merges stderr into stdout. Polling read
            // so cancellation is cooperative. 50 ms polls so writes never
            // wait more than ~50 ms for the session mutex.
            readStdoutJob = viewModelScope.launch(Dispatchers.IO) {
                val buf = ByteArray(4096)
                var total = 0L
                while (isActive) {
                    val n = try {
                        ch.readStdoutTimeout(buf, 50)
                    } catch (t: Throwable) {
                        Log.e(TAG, "read error", t)
                        _state.value = TerminalState.Error("read error: ${t.message}")
                        break
                    }
                    if (n == -2) continue // SSH_AGAIN: timeout, keep polling
                    if (n <= 0) {
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
    }

    /**
     * Forward a layout change to the local emulator (synchronous, cheap)
     * and to the remote PTY (suspending JNI call through the session
     * mutex, dispatched via the VM scope).
     */
    fun resize(cols: Int, rows: Int, cellW: Int, cellH: Int) {
        _session.value?.emulator?.resize(cols, rows, cellW, cellH)
        val currentIo = io ?: return
        viewModelScope.launch {
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
        drainReaderAndClose(job, session)
        sessionManager.disconnect(hostId)
    }

    /**
     * Stop the reader and close the channel, bounded by a short timeout
     * so we never hang the main thread. In the happy path the reader is
     * polling with a 200 ms timeout, so cancel + join returns in <=200 ms.
     * In a pathological case (session already corrupted, native read
     * wedged), we give up after a bit and leak the channel — this avoids
     * ANRs when the user taps Back on a broken session.
     */
    private fun drainReaderAndClose(job: Job?, session: SshTerminalSession?) {
        runBlocking {
            withTimeoutOrNull(800) {
                withContext(NonCancellable) {
                    try {
                        job?.cancel()
                        job?.join()
                    } catch (_: Throwable) {
                        // ignore
                    }
                    try {
                        session?.closeChannel()
                    } catch (_: Throwable) {
                        // ignore
                    }
                }
            }
        }
    }

    override fun onCleared() {
        Log.d(TAG, "onCleared — cancelling reader and closing channel")
        super.onCleared()
        val job = readStdoutJob
        val session = _session.value
        _session.value = null
        drainReaderAndClose(job, session)
    }

    companion object { private const val TAG = "TerminalVM" }
}
