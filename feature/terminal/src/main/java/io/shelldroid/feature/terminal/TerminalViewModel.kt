package io.shelldroid.feature.terminal

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.shelldroid.core.db.dao.HostDao
import io.shelldroid.core.ssh.SshSessionManager
import io.shelldroid.feature.terminal.skin.TerminalSkin
import io.shelldroid.feature.terminal.skin.TerminalSkinRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Thin wrapper around a [TerminalBridge]. The bridge holds all the
 * libssh <-> terminal emulator plumbing and outlives the VM for async
 * cleanup, so `onCleared` never blocks the main thread.
 */
@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val sessionManager: SshSessionManager,
    private val hostDao: HostDao,
    skinRepository: TerminalSkinRepository,
) : ViewModel() {

    val bridge: TerminalBridge = TerminalBridge(sessionManager)

    val skin: StateFlow<TerminalSkin> = skinRepository.selected
        .stateIn(viewModelScope, SharingStarted.Eagerly, skinRepository.current())

    private val _title = MutableStateFlow("Terminal")
    val title: StateFlow<String> = _title.asStateFlow()

    @Volatile private var attached: Boolean = false
    @Volatile private var currentHostId: String? = null

    /**
     * Attach to the warm SSH session for [hostId] and spin up the
     * emulator. Idempotent: repeat calls while attached are no-ops.
     * Intended to be called once from [TerminalScreen] inside a
     * [LaunchedEffect] or the [androidx.compose.ui.viewinterop.AndroidView]
     * factory / layout callback with the real grid dimensions.
     */
    fun attach(
        hostId: String,
        cols: Int,
        rows: Int,
        foreground: Int,
        background: Int,
    ) {
        if (attached) return
        attached = true
        currentHostId = hostId
        Log.d(TAG, "attach(host=$hostId ${cols}x${rows})")

        // Load the host title in the background (cosmetic only).
        viewModelScope.launch {
            val host = hostDao.findById(hostId) ?: return@launch
            _title.value = host.name.ifBlank { "${host.username}@${host.hostname}" }
        }

        bridge.attach(
            hostId = hostId,
            initialCols = cols,
            initialRows = rows,
            defaultForeground = foreground,
            defaultBackground = background,
        )
    }

    /**
     * Hard-disconnect the underlying SSH session. Called from the back
     * dialog's "Desconectar" option.
     */
    fun disconnectAndDetach() {
        Log.d(TAG, "disconnectAndDetach($currentHostId)")
        bridge.detach(disconnectSession = true)
        attached = false
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared — detaching bridge (session stays warm)")
        bridge.detach(disconnectSession = false)
        attached = false
    }

    companion object { private const val TAG = "TerminalVM" }
}
