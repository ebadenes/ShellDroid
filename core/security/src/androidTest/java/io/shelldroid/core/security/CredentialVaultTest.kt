package io.shelldroid.core.security

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialVaultTest {

    private lateinit var vault: CredentialVault

    @Before
    fun setUp() {
        vault = CredentialVault(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun encrypt_decrypt_chars_roundtrip() {
        val secret = "p@ssw0rd-ñ-漢".toCharArray()
        val cipher = vault.encrypt(secret.copyOf())
        val plain = vault.decrypt(cipher)
        assertThat(plain).isEqualTo(secret)
        // sanity: ciphertext is not the same as plaintext
        assertThat(cipher).isNotEqualTo(charsToUtf8Bytes(secret))
    }

    @Test
    fun encrypt_decrypt_bytes_roundtrip() {
        val payload = ByteArray(64) { it.toByte() }
        val cipher = vault.encryptBytes(payload)
        val back = vault.decryptBytes(cipher)
        assertThat(back).isEqualTo(payload)
    }

    @Test
    fun decrypted_chararray_is_a_fresh_instance() {
        val secret = "abc123".toCharArray()
        val cipher = vault.encrypt(secret.copyOf())
        val a = vault.decrypt(cipher)
        val b = vault.decrypt(cipher)
        assertThat(a).isNotSameInstanceAs(b)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun withDecrypted_zeroizes_after_block() {
        val cipher = vault.encrypt("secret".toCharArray())
        var captured: CharArray? = null
        vault.withDecrypted(cipher) { plain ->
            captured = plain
        }
        // After the helper, the buffer must be zeroed.
        assertThat(captured).isNotNull()
        assertThat(captured!!.all { it == '\u0000' }).isTrue()
    }
}
