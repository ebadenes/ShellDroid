package io.shelldroid.feature.terminal

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.shelldroid.core.ssh.ShellChannel
import io.shelldroid.core.ssh.SshSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {

    private val _state = MutableStateFlow<TerminalState>(TerminalState.Idle)
    val state: StateFlow<TerminalState> = _state.asStateFlow()

    private val _session = MutableStateFlow<SshTerminalSession?>(null)
    val session: StateFlow<SshTerminalSession?> = _session.asStateFlow()

    private var io: TerminalIo? = null
    private var channel: ShellChannel? = null
    private var readStdoutJob: Job? = null
    private var readStderrJob: Job? = null

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

        readStderrJob = viewModelScope.launch(Dispatchers.IO) {
            val buf = ByteArray(4096)
            while (isActive) {
                val n = try {
                    ch.readStderr(buf)
                } catch (_: Throwable) {
                    break
                }
                if (n <= 0) break
                val copy = buf.copyOf(n)
                session.feedFromShell(copy, n)
            }
        }
    }

    /** Forward a layout change to both the remote pty and the local emulator. */
    fun resize(cols: Int, rows: Int, cellW: Int, cellH: Int) {
        io?.resize(cols, rows)
        _session.value?.emulator?.resize(cols, rows, cellW, cellH)
    }

    override fun onCleared() {
        super.onCleared()
        readStdoutJob?.cancel()
        readStderrJob?.cancel()
        _session.value?.closeChannel()
        _session.value = null
    }
}
