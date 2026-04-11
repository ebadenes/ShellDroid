package io.shelldroid.ui.settings

import android.content.pm.PackageManager
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.shelldroid.core.db.AppPreferences
import io.shelldroid.core.db.dao.KnownHostDao
import io.shelldroid.core.security.AutoLockMode
import io.shelldroid.core.security.LockManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import io.shelldroid.core.ui.AppTheme
import io.shelldroid.core.ui.ThemeMode
import io.shelldroid.feature.terminal.skin.BuiltInSkins
import io.shelldroid.feature.terminal.skin.TerminalSkin
import io.shelldroid.feature.terminal.skin.TerminalSkinRepository
import kotlinx.coroutines.flow.first
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
    val autoLockMode: AutoLockMode = AutoLockMode.SYSTEM_SCREEN_OFF,
    val appVersion: String = "0.1.0-alpha",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val skinRepository: TerminalSkinRepository,
    private val knownHostDao: KnownHostDao,
    private val lockManager: LockManager,
    private val appPreferences: AppPreferences,
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
            _state.value = _state.value.copy(
                pinLockEnabled = lockManager.isLockEnabled(),
                keepScreenOn = appPreferences.keepScreenOnFlow.first(),
                autoLockMode = lockManager.getAutoLockMode(),
            )
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
        viewModelScope.launch { appPreferences.setKeepScreenOn(on) }
    }

    /**
     * Toggle the system-credential lock. Authentication itself is delegated
     * to BiometricPrompt with DEVICE_CREDENTIAL — there is no in-app PIN to
     * collect, so enabling is a single boolean flip.
     */
    fun setPinLock(enabled: Boolean) {
        _state.value = _state.value.copy(pinLockEnabled = enabled)
        viewModelScope.launch {
            lockManager.setLockEnabled(enabled)
            if (enabled) lockManager.markUnlocked()
        }
    }

    fun setAutoLockMode(mode: AutoLockMode) {
        _state.value = _state.value.copy(autoLockMode = mode)
        viewModelScope.launch { lockManager.setAutoLockMode(mode) }
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
