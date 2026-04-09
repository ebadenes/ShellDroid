package io.shelldroid.ui.settings

import android.content.pm.PackageManager
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.shelldroid.core.db.dao.KnownHostDao
import io.shelldroid.feature.terminal.skin.BuiltInSkins
import io.shelldroid.feature.terminal.skin.TerminalSkin
import io.shelldroid.feature.terminal.skin.TerminalSkinRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val selectedSkinId: String = BuiltInSkins.DEFAULT.id,
    val fontSizeSp: Float = BuiltInSkins.DEFAULT.textSizeSp,
    val keepScreenOn: Boolean = false,
    val pinLockEnabled: Boolean = false,
    val autoLockLabel: String = "5 minutos",
    val appVersion: String = "0.1.0-alpha",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val skinRepository: TerminalSkinRepository,
    private val knownHostDao: KnownHostDao,
    @ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    private val _state = mutableStateOf(
        SettingsState(
            selectedSkinId = skinRepository.current().id,
            fontSizeSp = skinRepository.current().textSizeSp,
            appVersion = getAppVersion(),
        )
    )
    val state: State<SettingsState> = _state

    val availableSkins: List<TerminalSkin> = skinRepository.available

    fun selectSkin(skinId: String) {
        skinRepository.select(skinId)
        val skin = skinRepository.current()
        _state.value = _state.value.copy(
            selectedSkinId = skin.id,
            fontSizeSp = skin.textSizeSp,
        )
    }

    fun setFontSize(sp: Float) {
        _state.value = _state.value.copy(fontSizeSp = sp)
        // TODO: persist to DataStore and propagate to TerminalScreen
        // For now the terminal picks up the skin's default on next open.
    }

    fun setKeepScreenOn(on: Boolean) {
        _state.value = _state.value.copy(keepScreenOn = on)
        // TODO: persist to DataStore. The actual FLAG_KEEP_SCREEN_ON is
        // applied by TerminalScreen via a DisposableEffect when this
        // pref is wired.
    }

    fun setPinLock(enabled: Boolean) {
        _state.value = _state.value.copy(pinLockEnabled = enabled)
        // TODO: wire to LockManager.setEnabled / BiometricGate
    }

    fun clearKnownHosts() {
        viewModelScope.launch {
            knownHostDao.deleteAll()
        }
    }

    private fun getAppVersion(): String = try {
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        info.versionName ?: "unknown"
    } catch (_: PackageManager.NameNotFoundException) {
        "unknown"
    }
}
