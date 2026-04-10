package io.shelldroid.ui.settings

import android.content.pm.PackageManager
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.shelldroid.core.db.dao.KnownHostDao
import io.shelldroid.core.security.LockManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import io.shelldroid.core.ui.AppTheme
import io.shelldroid.core.ui.ThemeMode
import io.shelldroid.feature.terminal.skin.BuiltInSkins
import io.shelldroid.feature.terminal.skin.TerminalSkin
import io.shelldroid.feature.terminal.skin.TerminalSkinRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Language code: null = system, "es" = Spanish, "en" = English */
enum class AppLanguage(val code: String?, val label: String) {
    SYSTEM(null, "System"),
    ES("es", "Español"),
    EN("en", "English"),
}

data class SettingsState(
    val selectedSkinId: String = BuiltInSkins.DEFAULT.id,
    val fontSizeSp: Float = BuiltInSkins.DEFAULT.textSizeSp,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val keepScreenOn: Boolean = false,
    val pinLockEnabled: Boolean = false,
    val autoLockLabel: String = "5 minutos",
    val appVersion: String = "0.1.0-alpha",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val skinRepository: TerminalSkinRepository,
    private val knownHostDao: KnownHostDao,
    private val lockManager: LockManager,
    @ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    private val _state = mutableStateOf(
        SettingsState(
            selectedSkinId = skinRepository.current().id,
            fontSizeSp = skinRepository.current().textSizeSp,
            themeMode = AppTheme.mode,
            language = currentLanguage(),
            appVersion = getAppVersion(),
        )
    )

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(pinLockEnabled = lockManager.hasPin())
        }
    }

    private fun currentLanguage(): AppLanguage {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return AppLanguage.SYSTEM
        val tag = locales.get(0)?.language ?: return AppLanguage.SYSTEM
        return AppLanguage.entries.firstOrNull { it.code == tag } ?: AppLanguage.SYSTEM
    }
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
        skinRepository.setFontSize(sp)
    }

    fun setThemeMode(mode: ThemeMode) {
        _state.value = _state.value.copy(themeMode = mode)
        AppTheme.mode = mode
    }

    fun setLanguage(lang: AppLanguage) {
        _state.value = _state.value.copy(language = lang)
        val locales = if (lang.code != null) {
            LocaleListCompat.forLanguageTags(lang.code)
        } else {
            LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun setKeepScreenOn(on: Boolean) {
        _state.value = _state.value.copy(keepScreenOn = on)
        // TODO: persist to DataStore. The actual FLAG_KEEP_SCREEN_ON is
        // applied by TerminalScreen via a DisposableEffect when this
        // pref is wired.
    }

    /**
     * When disabling PIN lock, clear immediately. When enabling, the UI must
     * first collect a PIN via [setNewPin] — this method only updates the state
     * optimistically when enabling (the actual PIN is set by [setNewPin]).
     */
    fun setPinLock(enabled: Boolean) {
        if (!enabled) {
            viewModelScope.launch {
                lockManager.clearPin()
                _state.value = _state.value.copy(pinLockEnabled = false)
            }
        }
        // When enabling, the SettingsScreen must show SetPinDialog; the actual
        // enabling happens in setNewPin().
    }

    /**
     * Called by the Set PIN dialog after the user confirms matching PINs.
     */
    fun setNewPin(pin: String) {
        viewModelScope.launch {
            lockManager.setPin(pin.toCharArray())
            lockManager.markUnlocked()
            _state.value = _state.value.copy(pinLockEnabled = true)
        }
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
