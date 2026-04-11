package io.shelldroid.core.ssh

import io.shelldroid.core.db.AuthType
import io.shelldroid.core.db.dao.HostDao
import io.shelldroid.core.db.dao.IdentityDao
import io.shelldroid.core.db.entities.Identity
import io.shelldroid.core.security.CredentialVault
import io.shelldroid.core.security.withDecrypted
import io.shelldroid.core.ssh.model.AuthMethod
import io.shelldroid.core.ssh.model.SshConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared use case that guarantees an SSH session exists for a given
 * `hostId`. If the session is already live it's a no-op; otherwise it
 * looks up the host, resolves its stored identity, decrypts the
 * credentials through [CredentialVault], and calls
 * [SshSessionManager.connect].
 *
 * Extracted so both `feature:hosts` (tap-to-connect on a host card)
 * and `feature:portforward` (tap Play on a forward — new since v1)
 * can trigger the same connect flow without duplicating auth logic.
 *
 * Hosts without a saved identity require a one-off password and
 * therefore cannot be auto-connected from the port-forward screen —
 * `ensureConnected` returns [Result.NeedsPassword] and the caller is
 * expected to route the user through the regular Hosts screen prompt.
 */
@Singleton
class HostConnector @Inject constructor(
    private val hostDao: HostDao,
    private val identityDao: IdentityDao,
    private val vault: CredentialVault,
    private val sessionManager: SshSessionManager,
) {
    sealed class Result {
        object Success : Result()
        object AlreadyConnected : Result()
        data class HostNotFound(val hostId: String) : Result()
        data class NeedsPassword(val hostId: String) : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun ensureConnected(hostId: String): Result {
        if (sessionManager.getClient(hostId) != null) return Result.AlreadyConnected

        val host = hostDao.findById(hostId) ?: return Result.HostNotFound(hostId)
        val identityId = host.identityId ?: return Result.NeedsPassword(hostId)
        val identity = identityDao.findById(identityId)
            ?: return Result.Error("Identity no encontrada")
        if (identity.needsReentry) {
            return Result.Error("Credencial invalidada — re-ingrésala")
        }
        val auth = try {
            buildAuthMethod(identity)
        } catch (t: Throwable) {
            return Result.Error("No se pudo descifrar la credencial: ${t.message}")
        }
        val config = SshConfig(
            hostId = host.id,
            hostname = host.hostname,
            port = host.port,
            username = host.username,
            auth = auth,
        )
        val r = sessionManager.connect(config)
        return if (r.isSuccess) {
            Result.Success
        } else {
            val err = r.exceptionOrNull()
            Result.Error(
                when (err) {
                    is SshConnectException.HostKeyRejected -> "Host key rechazado"
                    is SshConnectException.AuthFailed -> "Autenticación falló"
                    is SshConnectException.NetworkError -> "Error de red: ${err.message}"
                    else -> err?.message ?: "Error desconocido"
                }
            )
        }
    }

    private fun buildAuthMethod(identity: Identity): AuthMethod = when (identity.authType) {
        AuthType.PASSWORD -> AuthMethod.Password(
            password = vault.withDecrypted(identity.encryptedSecret) { it.copyOf() },
        )
        AuthType.KEY_RSA, AuthType.KEY_ED25519, AuthType.KEY_ECDSA -> AuthMethod.PublicKey(
            privateKeyPem = vault.decryptBytes(identity.encryptedSecret),
            passphrase = identity.encryptedPassphrase?.let { cp ->
                vault.withDecrypted(cp) { it.copyOf() }
            },
        )
    }
}
