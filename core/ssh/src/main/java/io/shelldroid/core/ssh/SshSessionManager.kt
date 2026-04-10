package io.shelldroid.core.ssh

import io.shelldroid.core.ssh.model.SshConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-wide registry of live SSH sessions, keyed by [SshConfig.hostId].
 *
 * Connect flow:
 *  1. allocate a [LibSshClient] and run `ssh_connect`
 *  2. extract the server public key blob and run [TofuHostKeyVerifier.verify]
 *  3. on accept → authenticate, register the session, return the client
 *  4. on reject → disconnect and throw [SshConnectException.HostKeyRejected]
 *
 * The map is `ConcurrentHashMap` so [activeCount] / [activeHostIds] are
 * lock-free, but `connect` is NOT idempotent: a second concurrent call for
 * the same `hostId` will leak the loser. Higher layers (UI ViewModels)
 * should serialize their own connect attempts per host.
 *
 * Foreground service hook: every successful [connect] calls
 * [SessionServiceController.ensureRunning], and [activeCountFlow] is the
 * source of truth that the service observes to update its notification and
 * stop itself once the count drops to zero.
 */
@Singleton
class SshSessionManager @Inject constructor(
    private val verifier: TofuHostKeyVerifier,
    private val serviceController: SessionServiceController = NoopSessionServiceController,
) {
    private val sessions = ConcurrentHashMap<String, LibSshClient>()
    /** Labels for the notification: hostId → "user@host:port" */
    private val sessionLabels = ConcurrentHashMap<String, String>()
    @Volatile private var _connectTime: Long = 0L

    private val _activeCountFlow = MutableStateFlow(0)
    /** Observable session count. The session service collects this. */
    val activeCountFlow: StateFlow<Int> = _activeCountFlow.asStateFlow()

    suspend fun connect(config: SshConfig): Result<LibSshClient> = runCatching {
        val client = LibSshClient()
        client.connect(config).getOrThrow()

        val blob = client.getServerPublicKeyBlob()
            ?: run {
                client.disconnect()
                throw SshConnectException.Unknown("server did not present a public key")
            }
        val keyType = client.getPublicKeyType(blob)

        val accepted = verifier.verify(config.hostname, config.port, keyType, blob)
        if (!accepted) {
            client.disconnect()
            throw SshConnectException.HostKeyRejected(
                hostname = config.hostname,
                port = config.port,
                reason = "TOFU verifier rejected the host key",
            )
        }

        client.authenticate(config.auth, config.username).getOrThrow()

        sessions.put(config.hostId, client)?.disconnect()  // close any stale entry
        sessionLabels[config.hostId] = "${config.username}@${config.hostname}:${config.port}"
        if (_connectTime == 0L) _connectTime = System.currentTimeMillis()
        _activeCountFlow.value = sessions.size
        serviceController.ensureRunning()
        client
    }

    fun getClient(hostId: String): LibSshClient? = sessions[hostId]

    fun disconnect(hostId: String) {
        sessions.remove(hostId)?.disconnect()
        sessionLabels.remove(hostId)
        if (sessions.isEmpty()) _connectTime = 0L
        _activeCountFlow.value = sessions.size
    }

    fun disconnectAll() {
        val snapshot = sessions.values.toList()
        sessions.clear()
        _activeCountFlow.value = 0
        snapshot.forEach { it.disconnect() }
    }

    fun activeCount(): Int = sessions.size

    fun activeHostIds(): Set<String> = sessions.keys.toSet()

    /** Labels for active sessions: "user@host:port" */
    fun activeLabels(): List<String> = sessionLabels.values.toList()

    /** Epoch millis when the first session connected (0 if none). */
    fun connectTimeMillis(): Long = _connectTime
}
