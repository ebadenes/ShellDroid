package io.shelldroid.core.security

import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Auto-lock policy options. Seconds == 0 means lock as soon as the app goes
 * to background; [NEVER] disables time-based locking entirely; [SYSTEM_SCREEN_OFF]
 * mirrors the user's system screen-off timeout (read from
 * [Settings.System.SCREEN_OFF_TIMEOUT]).
 */
enum class AutoLockMode(val seconds: Long) {
    /** Lock when the system would turn the screen off. Resolved at check time. */
    SYSTEM_SCREEN_OFF(-1),
    IMMEDIATE(0),
    ONE_MIN(60),
    FIVE_MIN(300),
    FIFTEEN_MIN(900),
    NEVER(Long.MAX_VALUE);

    companion object {
        fun fromName(name: String?): AutoLockMode =
            values().firstOrNull { it.name == name } ?: SYSTEM_SCREEN_OFF
    }
}

/**
 * Lock state and auto-lock timing for ShellDroid.
 *
 * Authentication is delegated entirely to the Android system credential
 * (BiometricPrompt with `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`) — there
 * is no in-app PIN. We store only:
 *  - `lockEnabled` = whether the user toggled the lock on
 *  - `lastUnlock` = epoch millis of the last successful unlock
 *  - `autoLockMode` = enum name
 *
 * The [CredentialVault] is no longer used here; it stays as a constructor
 * dep purely so the security module's Hilt graph stays stable. Older
 * builds that stored a PIN cipher leave that key orphaned in DataStore;
 * it is harmless and ignored.
 *
 * No in-memory state — surviving process death is required.
 */
@Singleton
class LockManager @Inject constructor(
    @Suppress("unused") private val vault: CredentialVault,
    @SecurityDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context,
) {

    suspend fun isLocked(): Boolean {
        if (!isLockEnabled()) return false
        val mode = getAutoLockMode()
        if (mode == AutoLockMode.NEVER) {
            // NEVER: only locked if we never unlocked since enabling.
            val last = dataStore.data.first()[KEY_LAST_UNLOCK] ?: 0L
            return last == 0L
        }
        val last = dataStore.data.first()[KEY_LAST_UNLOCK] ?: 0L
        if (last == 0L) return true
        val timeoutMs = if (mode == AutoLockMode.SYSTEM_SCREEN_OFF) {
            systemScreenOffTimeoutMs()
        } else {
            mode.seconds * 1000L
        }
        val deadline = last + timeoutMs
        return System.currentTimeMillis() >= deadline
    }

    /**
     * Reads the user's system screen-off timeout (the same value Android
     * uses to turn the screen off automatically). Returns 30s as a safe
     * default if the system value is unavailable.
     */
    private fun systemScreenOffTimeoutMs(): Long = try {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_OFF_TIMEOUT,
            30_000,
        ).toLong()
    } catch (_: Settings.SettingNotFoundException) {
        30_000L
    }

    suspend fun markUnlocked() {
        dataStore.edit { it[KEY_LAST_UNLOCK] = System.currentTimeMillis() }
    }

    suspend fun isLockEnabled(): Boolean =
        dataStore.data.first()[KEY_LOCK_ENABLED] ?: false

    suspend fun setLockEnabled(enabled: Boolean) {
        dataStore.edit {
            it[KEY_LOCK_ENABLED] = enabled
            if (!enabled) it.remove(KEY_LAST_UNLOCK)
        }
    }

    suspend fun getAutoLockMode(): AutoLockMode =
        AutoLockMode.fromName(dataStore.data.first()[KEY_AUTO_LOCK])

    suspend fun setAutoLockMode(mode: AutoLockMode) {
        dataStore.edit { it[KEY_AUTO_LOCK] = mode.name }
    }

    companion object {
        private val KEY_LOCK_ENABLED = booleanPreferencesKey("lock_enabled")
        private val KEY_LAST_UNLOCK = longPreferencesKey("last_unlock")
        private val KEY_AUTO_LOCK = stringPreferencesKey("auto_lock_mode")
    }
}
