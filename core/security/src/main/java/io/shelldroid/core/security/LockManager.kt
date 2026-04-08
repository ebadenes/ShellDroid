package io.shelldroid.core.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Auto-lock policy options. Seconds == 0 means lock as soon as the app goes
 * to background; [NEVER] disables time-based locking entirely.
 */
enum class AutoLockMode(val seconds: Long) {
    IMMEDIATE(0),
    ONE_MIN(60),
    FIVE_MIN(300),
    FIFTEEN_MIN(900),
    NEVER(Long.MAX_VALUE);

    companion object {
        fun fromName(name: String?): AutoLockMode =
            values().firstOrNull { it.name == name } ?: FIVE_MIN
    }
}

/**
 * Lock state, PIN storage and auto-lock timing for ShellDroid.
 *
 * Persistence model (§5.8):
 *  - PIN material = `salt(32B) || pbkdf2_hash(32B)`, encrypted via [CredentialVault]
 *    and stored Base64 in DataStore.
 *  - `lastUnlock` = epoch millis of the last successful unlock.
 *  - `autoLockMode` = enum name.
 *
 * No in-memory state — surviving process death is required.
 */
@Singleton
class LockManager @Inject constructor(
    private val vault: CredentialVault,
    private val dataStore: DataStore<Preferences>
) {

    suspend fun isLocked(): Boolean {
        if (!hasPin()) return false
        val mode = getAutoLockMode()
        if (mode == AutoLockMode.NEVER) {
            // NEVER: only locked if we never unlocked since install.
            val last = dataStore.data.first()[KEY_LAST_UNLOCK] ?: 0L
            return last == 0L
        }
        val last = dataStore.data.first()[KEY_LAST_UNLOCK] ?: 0L
        if (last == 0L) return true
        val deadline = last + mode.seconds * 1000L
        return System.currentTimeMillis() >= deadline
    }

    suspend fun markUnlocked() {
        dataStore.edit { it[KEY_LAST_UNLOCK] = System.currentTimeMillis() }
    }

    suspend fun setPin(pin: CharArray) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt, ITERATIONS, KEY_LEN_BITS)
        val payload = salt + hash // 32 + 32
        try {
            val cipher = vault.encryptBytes(payload)
            dataStore.edit {
                it[KEY_PIN_CIPHER] = Base64.getEncoder().encodeToString(cipher)
            }
        } finally {
            hash.fill(0)
            payload.fill(0)
        }
    }

    suspend fun verifyPin(pin: CharArray): Boolean {
        val encoded = dataStore.data.first()[KEY_PIN_CIPHER] ?: return false
        val cipher = Base64.getDecoder().decode(encoded)
        val payload = vault.decryptBytes(cipher)
        var candidate: ByteArray? = null
        try {
            if (payload.size != SALT_BYTES + KEY_LEN_BITS / 8) return false
            val salt = payload.copyOfRange(0, SALT_BYTES)
            val expected = payload.copyOfRange(SALT_BYTES, payload.size)
            candidate = pbkdf2(pin, salt, ITERATIONS, KEY_LEN_BITS)
            return MessageDigest.isEqual(candidate, expected)
        } finally {
            payload.fill(0)
            candidate?.fill(0)
        }
    }

    suspend fun hasPin(): Boolean =
        dataStore.data.first()[KEY_PIN_CIPHER] != null

    suspend fun clearPin() {
        dataStore.edit {
            it.remove(KEY_PIN_CIPHER)
            it.remove(KEY_LAST_UNLOCK)
        }
    }

    suspend fun getAutoLockMode(): AutoLockMode =
        AutoLockMode.fromName(dataStore.data.first()[KEY_AUTO_LOCK])

    suspend fun setAutoLockMode(mode: AutoLockMode) {
        dataStore.edit { it[KEY_AUTO_LOCK] = mode.name }
    }

    private fun pbkdf2(pin: CharArray, salt: ByteArray, iter: Int, lenBits: Int): ByteArray {
        val spec = PBEKeySpec(pin, salt, iter, lenBits)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        try {
            return skf.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val ITERATIONS = 200_000
        const val KEY_LEN_BITS = 256
        const val SALT_BYTES = 32
        private val KEY_PIN_CIPHER = stringPreferencesKey("pin_cipher")
        private val KEY_LAST_UNLOCK = longPreferencesKey("last_unlock")
        private val KEY_AUTO_LOCK = stringPreferencesKey("auto_lock_mode")
    }
}
