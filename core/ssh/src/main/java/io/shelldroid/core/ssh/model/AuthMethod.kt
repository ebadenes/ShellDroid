package io.shelldroid.core.ssh.model

/**
 * Authentication credentials passed to [io.shelldroid.core.ssh.LibSshClient.authenticate].
 *
 * Sensitive material is held as `CharArray`/`ByteArray` so callers can
 * zero-out the buffers after use. NEVER convert these to `String`.
 */
sealed class AuthMethod {
    data class Password(val password: CharArray) : AuthMethod() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Password) return false
            return password.contentEquals(other.password)
        }
        override fun hashCode(): Int = password.contentHashCode()
        override fun toString(): String = "Password(***)"
    }

    data class PublicKey(
        val privateKeyPem: ByteArray,
        val passphrase: CharArray?,
    ) : AuthMethod() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PublicKey) return false
            if (!privateKeyPem.contentEquals(other.privateKeyPem)) return false
            if (passphrase == null) return other.passphrase == null
            if (other.passphrase == null) return false
            return passphrase.contentEquals(other.passphrase)
        }
        override fun hashCode(): Int {
            var r = privateKeyPem.contentHashCode()
            r = 31 * r + (passphrase?.contentHashCode() ?: 0)
            return r
        }
        override fun toString(): String = "PublicKey(***)"
    }

    /** Method-name string used in [io.shelldroid.core.ssh.SshConnectException.AuthFailed]. */
    val methodName: String
        get() = when (this) {
            is Password -> "password"
            is PublicKey -> "publickey"
        }
}
