package io.shelldroid.feature.hosts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.shelldroid.core.db.AuthType
import io.shelldroid.core.db.entities.Host
import io.shelldroid.core.db.entities.Identity
import io.shelldroid.core.security.CredentialVault
import io.shelldroid.core.security.withDecrypted
import io.shelldroid.core.ssh.PendingAutoCommand
import io.shelldroid.core.ssh.SshConnectException
import io.shelldroid.core.ssh.SshSessionManager
import io.shelldroid.core.ssh.model.AuthMethod
import io.shelldroid.core.ssh.model.SshConfig
import io.shelldroid.feature.hosts.data.HostRepository
import io.shelldroid.feature.hosts.data.IdentityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HostsListViewModel @Inject constructor(
    private val repo: HostRepository,
    private val identityRepo: IdentityRepository,
    private val sessionManager: SshSessionManager,
    private val vault: CredentialVault,
) : ViewModel() {

    val hosts: StateFlow<List<Host>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    sealed class ConnectState {
        data object Idle : ConnectState()
        data class Connecting(val hostId: String) : ConnectState()
        data class Connected(val hostId: String) : ConnectState()
        data class Error(val hostId: String, val message: String) : ConnectState()
        data class NeedsPassword(val hostId: String) : ConnectState()
    }

    private val _connectState = MutableStateFlow<ConnectState>(ConnectState.Idle)
    val connectState: StateFlow<ConnectState> = _connectState.asStateFlow()
    @Volatile private var _pendingPassword: String? = null

    /** Set password then retry connect for hosts without identity. */
    fun connectWithPassword(hostId: String, password: String) {
        _pendingPassword = password
        connect(hostId)
    }

    fun connect(hostId: String) {
        viewModelScope.launch {
            _connectState.value = ConnectState.Connecting(hostId)

            // Reuse an existing warm session if the user is reconnecting
            // (typical: back from terminal -> tap connect again).
            if (sessionManager.getClient(hostId) != null) {
                _connectState.value = ConnectState.Connected(hostId)
                return@launch
            }

            val host = repo.findById(hostId)
            if (host == null) {
                _connectState.value = ConnectState.Error(hostId, "Host no encontrado")
                return@launch
            }
            // Build auth method: if host has an identity, use it;
            // otherwise check if a password was provided via
            // pendingPasswordForHost, or fail with NeedsPassword state.
            val authMethod = run {
                val identityId = host.identityId
                if (identityId != null) {
                    val identity = identityRepo.findById(identityId)
                    if (identity == null) {
                        _connectState.value = ConnectState.Error(hostId, "Identity no encontrada")
                        return@launch
                    }
                    if (identity.needsReentry) {
                        _connectState.value = ConnectState.Error(
                            hostId, "Credencial invalidada — re-ingresala",
                        )
                        return@launch
                    }
                    try {
                        buildAuthMethod(identity)
                    } catch (t: Throwable) {
                        _connectState.value = ConnectState.Error(
                            hostId, "No se pudo descifrar la credencial: ${t.message}",
                        )
                        return@launch
                    }
                } else {
                    // No identity — use pending password if available
                    val pw = _pendingPassword
                    _pendingPassword = null
                    if (pw.isNullOrEmpty()) {
                        _connectState.value = ConnectState.NeedsPassword(hostId)
                        return@launch
                    }
                    AuthMethod.Password(pw.toCharArray())
                }
            }
            val config = SshConfig(
                hostId = host.id,
                hostname = host.hostname,
                port = host.port,
                username = host.username,
                auth = authMethod,
            )
            val result = sessionManager.connect(config)
            _connectState.value = if (result.isSuccess) {
                repo.updateLastConnected(host.id, System.currentTimeMillis())
                if (host.autoCommand.isNotBlank()) {
                    PendingAutoCommand.set(host.id, host.autoCommand)
                }
                ConnectState.Connected(host.id)
            } else {
                val err = result.exceptionOrNull()
                val msg = when (err) {
                    is SshConnectException.HostKeyRejected -> "Host key rechazado"
                    is SshConnectException.AuthFailed -> "Autenticación falló"
                    is SshConnectException.NetworkError -> "Error de red: ${err.message}"
                    else -> err?.message ?: "Error desconocido"
                }
                ConnectState.Error(host.id, msg)
            }
        }
    }

    fun delete(host: Host) {
        viewModelScope.launch { repo.delete(host) }
    }

    fun clone(host: Host) {
        viewModelScope.launch {
            val copy = host.copy(
                id = UUID.randomUUID().toString(),
                name = "${host.name} (copia)",
                createdAt = System.currentTimeMillis(),
                lastConnectedAt = null,
            )
            repo.upsert(copy)
        }
    }

    fun quickConnect(
        hostname: String,
        port: Int,
        username: String,
        password: String,
        saveToDb: Boolean = false,
    ) {
        val hostId = UUID.randomUUID().toString()
        viewModelScope.launch {
            _connectState.value = ConnectState.Connecting(hostId)

            // Optionally save to DB first
            if (saveToDb) {
                val host = Host(
                    id = hostId,
                    userId = repo.currentUserId(),
                    name = "$username@$hostname",
                    hostname = hostname,
                    port = port,
                    username = username,
                    createdAt = System.currentTimeMillis(),
                )
                repo.upsert(host)
            }

            val auth = if (password.isNotEmpty()) {
                AuthMethod.Password(password.toCharArray())
            } else {
                // Try none/keyboard-interactive — server may allow it
                AuthMethod.Password(CharArray(0))
            }
            val config = SshConfig(
                hostId = hostId,
                hostname = hostname,
                port = port,
                username = username,
                auth = auth,
            )
            val result = sessionManager.connect(config)
            _connectState.value = if (result.isSuccess) {
                ConnectState.Connected(hostId)
            } else {
                val err = result.exceptionOrNull()
                val msg = when (err) {
                    is SshConnectException.HostKeyRejected -> "Host key rechazado"
                    is SshConnectException.AuthFailed -> "Autenticación falló"
                    is SshConnectException.NetworkError -> "Error de red: ${err.message}"
                    else -> err?.message ?: "Error desconocido"
                }
                ConnectState.Error(hostId, msg)
            }
        }
    }

    fun resetConnectState() {
        _connectState.value = ConnectState.Idle
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
