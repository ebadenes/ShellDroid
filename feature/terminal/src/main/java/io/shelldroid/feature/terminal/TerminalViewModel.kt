package io.shelldroid.feature.terminal

import android.os.Handler
import android.os.Looper
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
        if (_session.value != null) return
        _state.value = TerminalState.Connecting

        val client = sessionManager.getClient(hostId)
        if (client == null) {
            _state.value = TerminalState.Error("no active session for host $hostId")
            return
        }

        val ch: ShellChannel = try {
            client.openShell(cols, rows)
        } catch (t: Throwable) {
            _state.value = TerminalState.Error("openShell failed: ${t.message}")
            return
        }
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
            while (isActive) {
                val n = try {
                    ch.readStdout(buf)
                } catch (t: Throwable) {
                    _state.value = TerminalState.Error("read error: ${t.message}")
                    break
                }
                if (n <= 0) break
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

    override fun onCleared() {
        super.onCleared()
        readStdoutJob?.cancel()
        _session.value?.closeChannel()
        _session.value = null
    }
}
