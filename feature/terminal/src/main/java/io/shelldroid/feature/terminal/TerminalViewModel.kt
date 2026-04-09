package io.shelldroid.feature.terminal

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.shelldroid.core.db.dao.HostDao
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
 * Thin presenter around a [TerminalBridge] fetched from the
 * [TerminalBridgeRegistry]. The bridge OUTLIVES this VM — when the
 * user hits back on the terminal screen the VM is destroyed but the
 * bridge keeps running (reader, writer, emulator, shell channel) so
 * coming back to the terminal screen is instantaneous and never tears
 * down the libssh session state mid-flight (the BR1 race).
 *
 * Explicit disconnect via [disconnectAndRelease] releases the bridge
 * from the registry and tears it down.
 */
@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val hostDao: HostDao,
    private val bridgeRegistry: TerminalBridgeRegistry,
    skinRepository: TerminalSkinRepository,
) : ViewModel() {

    val skin: StateFlow<TerminalSkin> = skinRepository.selected
        .stateIn(viewModelScope, SharingStarted.Eagerly, skinRepository.current())

    private val _title = MutableStateFlow("Terminal")
    val title: StateFlow<String> = _title.asStateFlow()

    @Volatile private var currentHostId: String? = null

    private val _bridge = MutableStateFlow<TerminalBridge?>(null)
    /**
     * Backing [TerminalBridge] for the current [hostId]. Call [attach]
     * first to populate. Null before attach or after
     * [disconnectAndRelease]. Exposed as a [StateFlow] so the Compose
     * screen can recompose when it becomes available.
     */
    val bridge: StateFlow<TerminalBridge?> = _bridge.asStateFlow()

    /**
     * Attach to the warm SSH session for [hostId]. Idempotent —
     * subsequent calls while already attached to the same host are
     * no-ops, and calls for a different host while the previous one
     * is still alive implicitly rebind (but this VM is per-screen
     * anyway so that should not happen in practice).
     *
     * The bridge is fetched from the [TerminalBridgeRegistry] so the
     * same instance is returned across VM lifecycles for the same host.
     */
    fun attach(
        hostId: String,
        cols: Int,
        rows: Int,
        foreground: Int,
        background: Int,
    ) {
        if (currentHostId == hostId && _bridge.value != null) return
        currentHostId = hostId
        Log.d(TAG, "attach(host=$hostId ${cols}x${rows})")

        viewModelScope.launch {
            val host = hostDao.findById(hostId) ?: return@launch
            _title.value = host.name.ifBlank { "${host.username}@${host.hostname}" }
        }

        val b = bridgeRegistry.getOrCreate(hostId)
        _bridge.value = b
        b.attach(
            hostId = hostId,
            initialCols = cols,
            initialRows = rows,
            defaultForeground = foreground,
            defaultBackground = background,
        )
    }

    /**
     * Hard-disconnect: release the bridge from the registry AND
     * disconnect the underlying SSH session from [SshSessionManager].
     * Called from the back dialog's "Desconectar" option.
     */
    fun disconnectAndRelease() {
        val host = currentHostId ?: return
        Log.d(TAG, "disconnectAndRelease($host)")
        bridgeRegistry.release(host, disconnectSession = true)
        _bridge.value = null
        currentHostId = null
    }

    override fun onCleared() {
        super.onCleared()
        // DO NOT detach the bridge. It lives in TerminalBridgeRegistry
        // and outlives this VM by design. The next TerminalScreen /
        // TerminalViewModel for the same host will get the same bridge
        // back instantly with zero reconnect work.
        Log.d(TAG, "onCleared — bridge left alive in registry for $currentHostId")
    }

    companion object { private const val TAG = "TerminalVM" }
}
