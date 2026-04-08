package io.shelldroid.feature.hosts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.shelldroid.core.db.AuthType
import io.shelldroid.core.db.entities.Host
import io.shelldroid.core.db.entities.Identity
import io.shelldroid.core.security.CredentialVault
import io.shelldroid.core.security.withDecrypted
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
    }

    private val _connectState = MutableStateFlow<ConnectState>(ConnectState.Idle)
    val connectState: StateFlow<ConnectState> = _connectState.asStateFlow()

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
            val identityId = host.identityId
            if (identityId == null) {
                _connectState.value = ConnectState.Error(hostId, "Host sin identity asociada")
                return@launch
            }
            val identity = identityRepo.findById(identityId)
            if (identity == null) {
                _connectState.value = ConnectState.Error(hostId, "Identity no encontrada")
                return@launch
            }
            if (identity.needsReentry) {
                _connectState.value = ConnectState.Error(
                    hostId,
                    "Credencial invalidada — re-ingresala",
                )
                return@launch
            }
            val authMethod = try {
                buildAuthMethod(identity)
            } catch (t: Throwable) {
                _connectState.value = ConnectState.Error(
                    hostId,
                    "No se pudo descifrar la credencial: ${t.message}",
                )
                return@launch
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
