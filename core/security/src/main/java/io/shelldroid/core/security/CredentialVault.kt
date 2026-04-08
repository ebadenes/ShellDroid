package io.shelldroid.core.security

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Symmetric envelope encryption for ShellDroid secrets (SSH passwords, key
 * passphrases, PIN material, etc) backed by Tink AES-256-GCM with the master
 * key in Android Keystore.
 *
 * Public API works with [CharArray] / [ByteArray] only — never [String] —
 * so callers can zeroize the cleartext after use. See §5.4 / §5.6 of the spec.
 */
@Singleton
class CredentialVault @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val aead: Aead by lazy { buildAead() }

    /** Encrypt a UTF-8 [CharArray]. The intermediate byte[] is zeroized. */
    fun encrypt(plaintext: CharArray): ByteArray {
        val bytes = charsToUtf8Bytes(plaintext)
        try {
            return encryptBytes(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    /**
     * Decrypt to a [CharArray]. The intermediate byte[] is zeroized.
     * Throws [VaultKeyInvalidatedException] if the keystore master key was
     * invalidated (e.g. user re-enrolled biometrics) — see §5.4 policy (b).
     */
    fun decrypt(data: ByteArray): CharArray {
        val plain = decryptBytes(data)
        try {
            return utf8BytesToChars(plain)
        } finally {
            plain.fill(0)
        }
    }

    /** Encrypt raw bytes (PIN salt+hash payloads, blobs, etc). */
    fun encryptBytes(plaintext: ByteArray): ByteArray = aead.encrypt(plaintext, null)

    /** Decrypt raw bytes; maps Keystore invalidation to [VaultKeyInvalidatedException]. */
    fun decryptBytes(data: ByteArray): ByteArray = try {
        aead.decrypt(data, null)
    } catch (e: KeyPermanentlyInvalidatedException) {
        throw VaultKeyInvalidatedException(e)
    } catch (e: GeneralSecurityException) {
        // Unwrap nested KeyPermanentlyInvalidatedException, if any.
        var cause: Throwable? = e.cause
        while (cause != null) {
            if (cause is KeyPermanentlyInvalidatedException) {
                throw VaultKeyInvalidatedException(cause)
            }
            cause = cause.cause
        }
        throw e
    }

    private fun buildAead(): Aead {
        AeadConfig.register()
        val handle = AndroidKeysetManager.Builder()
            .withSharedPref(ctx, KEYSET_NAME, PREF_FILE)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
        return handle.getPrimitive(Aead::class.java)
    }

    companion object {
        private const val KEYSET_NAME = "shelldroid_vault_keyset"
        private const val PREF_FILE = "shelldroid_vault_prefs"
        private const val MASTER_KEY_URI = "android-keystore://shelldroid_master_key"
    }
}

class VaultKeyInvalidatedException(cause: Throwable) : RuntimeException(cause)

/**
 * Decrypts [data], hands the cleartext [CharArray] to [block], and zeroizes it
 * in `finally` regardless of how [block] returns. This is the **only** sanctioned
 * way to consume vault-stored secrets (see §5.6 rule 2).
 */
inline fun <T> CredentialVault.withDecrypted(data: ByteArray, block: (CharArray) -> T): T {
    val plain = decrypt(data)
    try {
        return block(plain)
    } finally {
        plain.fill('\u0000')
    }
}

