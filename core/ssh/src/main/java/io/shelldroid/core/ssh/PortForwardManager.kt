package io.shelldroid.core.ssh

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * State of a single port forward tunnel.
 */
enum class ForwardState {
    STOPPED, STARTING, ACTIVE, ERROR
}

data class ForwardStatus(
    val forwardId: String,
    val state: ForwardState,
    val error: String? = null,
    val activeConnections: Int = 0,
)

/**
 * Manages running LOCAL port forward tunnels. Each tunnel:
 * 1. Opens a [ServerSocket] on the specified local port
 * 2. For each accepted connection, opens a direct-tcpip channel via
 *    [LibSshClient.openForward] and pipes data bidirectionally
 * 3. Can be stopped, which cancels the coroutine scope and closes
 *    all associated resources
 *
 * REMOTE and DYNAMIC forwarding are not yet implemented (TODO).
 */
@Singleton
class PortForwardManager @Inject constructor(
    private val sessionManager: SshSessionManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tunnels = java.util.concurrent.ConcurrentHashMap<String, TunnelState>()

    private val _statuses = MutableStateFlow<Map<String, ForwardStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, ForwardStatus>> = _statuses.asStateFlow()

    private class TunnelState(
        val job: Job,
        val serverSocket: ServerSocket,
    )

    /**
     * Starts a LOCAL port forward tunnel.
     *
     * @param forwardId unique ID of the port forward entity
     * @param hostId SSH host to forward through (must already be connected)
     * @param localPort local port to listen on
     * @param remoteHost remote host to connect to via the SSH tunnel
     * @param remotePort remote port to connect to
     */
    fun startLocal(
        forwardId: String,
        hostId: String,
        localPort: Int,
        remoteHost: String,
        remotePort: Int,
    ) {
        if (tunnels.containsKey(forwardId)) return

        updateStatus(forwardId, ForwardState.STARTING)

        val job = scope.launch {
            var serverSocket: ServerSocket? = null
            try {
                val client = sessionManager.getClient(hostId)
                    ?: throw IllegalStateException("No active SSH session for host $hostId")

                serverSocket = ServerSocket(localPort)
                tunnels[forwardId] = TunnelState(coroutineContext[Job]!!, serverSocket)
                updateStatus(forwardId, ForwardState.ACTIVE)

                while (isActive) {
                    val socket = try {
                        serverSocket.accept()
                    } catch (e: IOException) {
                        if (isActive) throw e else break
                    }

                    launch {
                        handleConnection(forwardId, client, socket, remoteHost, remotePort, localPort)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateStatus(forwardId, ForwardState.ERROR, e.message)
            } finally {
                serverSocket?.runCatching { close() }
                tunnels.remove(forwardId)
                val current = _statuses.value[forwardId]
                if (current?.state != ForwardState.ERROR) {
                    updateStatus(forwardId, ForwardState.STOPPED)
                }
            }
        }

        // Store tunnel state early for the stop path
        if (!tunnels.containsKey(forwardId)) {
            // ServerSocket hasn't been created yet; store with a placeholder
            // that will be replaced once the coroutine progresses
        }
    }

    /** Stops a running tunnel by ID. */
    fun stop(forwardId: String) {
        val tunnel = tunnels.remove(forwardId) ?: return
        tunnel.serverSocket.runCatching { close() }
        tunnel.job.cancel()
        updateStatus(forwardId, ForwardState.STOPPED)
    }

    /** Stops all running tunnels. */
    fun stopAll() {
        tunnels.keys.toList().forEach { stop(it) }
    }

    fun isRunning(forwardId: String): Boolean =
        tunnels[forwardId]?.job?.isActive == true

    private suspend fun handleConnection(
        forwardId: String,
        client: LibSshClient,
        socket: Socket,
        remoteHost: String,
        remotePort: Int,
        localPort: Int,
    ) {
        var channel: ForwardChannel? = null
        try {
            channel = client.openForward(remoteHost, remotePort, localPort)
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // Launch two coroutines: local->remote and remote->local
            val scope = CoroutineScope(coroutineContext + SupervisorJob())

            val localToRemote = scope.launch {
                val buf = ByteArray(16384)
                try {
                    while (isActive) {
                        val n = input.read(buf)
                        if (n < 0) break
                        channel.write(buf, 0, n)
                    }
                } catch (_: IOException) {
                    // Connection closed
                }
            }

            val remoteToLocal = scope.launch {
                val buf = ByteArray(16384)
                try {
                    while (isActive && !channel.isEof()) {
                        val n = channel.read(buf, timeoutMs = 100)
                        if (n > 0) {
                            output.write(buf, 0, n)
                            output.flush()
                        } else if (n < 0 && n != -2) {
                            // SSH_AGAIN (-2) means timeout, anything else negative is error
                            break
                        }
                    }
                } catch (_: IOException) {
                    // Connection closed
                }
            }

            // Wait for either direction to finish
            localToRemote.join()
            remoteToLocal.cancel()
            scope.cancel()
        } catch (_: Exception) {
            // Forward channel open failed or other error for this connection
        } finally {
            channel?.runCatching { close() }
            socket.runCatching { close() }
        }
    }

    private fun updateStatus(forwardId: String, state: ForwardState, error: String? = null) {
        _statuses.value = _statuses.value.toMutableMap().apply {
            put(forwardId, ForwardStatus(forwardId, state, error))
        }
    }
}
