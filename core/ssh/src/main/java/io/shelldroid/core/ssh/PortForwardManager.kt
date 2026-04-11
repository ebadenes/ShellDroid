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
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
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
 * Manages running port forward tunnels. Supports:
 *
 *  - **LOCAL** ([startLocal]): opens a [ServerSocket] on `localPort` and
 *    forwards every accepted connection to a fixed `remoteHost:remotePort`
 *    via a direct-tcpip channel.
 *  - **DYNAMIC** ([startDynamic]): opens a [ServerSocket] on `localPort`
 *    and speaks SOCKS5 on it. For each connection it performs the SOCKS5
 *    greeting + CONNECT handshake to extract the client's target address,
 *    then opens a direct-tcpip channel to that address — effectively a
 *    SOCKS5-over-SSH proxy (equivalent to `ssh -D`).
 *
 * Each tunnel runs in its own coroutine off the manager's IO scope; both
 * types can be stopped individually with [stop] or all at once with
 * [stopAll].
 *
 * REMOTE forwarding (`ssh -R`) is tracked in the post-v1 TODO.
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

    /**
     * Starts a DYNAMIC port forward tunnel (SOCKS5 proxy over SSH).
     *
     * @param forwardId unique ID of the port forward entity
     * @param hostId SSH host to forward through (must already be connected)
     * @param localPort local port to listen on (the SOCKS5 proxy port)
     */
    fun startDynamic(
        forwardId: String,
        hostId: String,
        localPort: Int,
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
                        handleSocksConnection(forwardId, client, socket, localPort)
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

    /**
     * SOCKS5 proxy handler (RFC 1928). Performs the minimal subset:
     *
     *  1. Greeting — client sends `[VER=5, NMETHODS, METHODS...]`; we
     *     reply `[5, 0]` (no-auth). We do not support user/pass auth.
     *  2. Request — client sends `[VER=5, CMD, RSV, ATYP, DST.ADDR, DST.PORT]`.
     *     We only accept CMD=1 (CONNECT). ATYP can be 1 (IPv4), 3 (domain),
     *     or 4 (IPv6).
     *  3. Reply — we send `[5, 0, 0, 1, 0.0.0.0, 0]` on success, then
     *     bridge bytes bidirectionally through the SSH direct-tcpip
     *     channel to the requested target.
     *
     * Any protocol error closes the socket with an appropriate SOCKS5
     * reply code and tears down the coroutine.
     */
    private suspend fun handleSocksConnection(
        forwardId: String,
        client: LibSshClient,
        socket: Socket,
        localPort: Int,
    ) {
        var channel: ForwardChannel? = null
        try {
            socket.tcpNoDelay = true
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())

            // ── greeting ───────────────────────────────────────────────
            val ver = input.readUnsignedByte()
            if (ver != 0x05) return
            val nMethods = input.readUnsignedByte()
            val methods = ByteArray(nMethods)
            input.readFully(methods)
            // reply: version 5, method 0 (NO AUTH). We don't care what
            // the client offered; if 0 isn't in the list it'll fail.
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()

            // ── request ────────────────────────────────────────────────
            val ver2 = input.readUnsignedByte()
            val cmd = input.readUnsignedByte()
            input.readUnsignedByte() // RSV
            val atyp = input.readUnsignedByte()
            if (ver2 != 0x05 || cmd != 0x01) {
                // command not supported
                writeSocksReply(output, rep = 0x07)
                return
            }

            val targetHost: String = when (atyp) {
                0x01 -> { // IPv4
                    val addr = ByteArray(4)
                    input.readFully(addr)
                    InetAddress.getByAddress(addr).hostAddress ?: "0.0.0.0"
                }
                0x03 -> { // domain
                    val len = input.readUnsignedByte()
                    val name = ByteArray(len)
                    input.readFully(name)
                    String(name, Charsets.US_ASCII)
                }
                0x04 -> { // IPv6
                    val addr = ByteArray(16)
                    input.readFully(addr)
                    InetAddress.getByAddress(addr).hostAddress ?: "::"
                }
                else -> {
                    writeSocksReply(output, rep = 0x08) // address type not supported
                    return
                }
            }
            val targetPort = input.readUnsignedShort()

            // ── open SSH channel ───────────────────────────────────────
            val sourceHost = socket.inetAddress?.hostAddress ?: "127.0.0.1"
            channel = try {
                client.openForward(targetHost, targetPort, localPort, sourceHost)
            } catch (e: Exception) {
                // Host unreachable / refused on the server side.
                writeSocksReply(output, rep = 0x05) // connection refused
                throw e
            }

            // ── reply: success ─────────────────────────────────────────
            writeSocksReply(output, rep = 0x00)

            // ── bidirectional pipe (identical to LOCAL path) ───────────
            val pipeScope = CoroutineScope(coroutineContext + SupervisorJob())

            val localToRemote = pipeScope.launch {
                val buf = ByteArray(16384)
                try {
                    while (isActive) {
                        val n = input.read(buf)
                        if (n < 0) break
                        channel.write(buf, 0, n)
                    }
                } catch (_: IOException) {
                }
            }

            val remoteToLocal = pipeScope.launch {
                val buf = ByteArray(16384)
                try {
                    while (isActive && !channel.isEof()) {
                        val n = channel.read(buf, timeoutMs = 100)
                        if (n > 0) {
                            output.write(buf, 0, n)
                            output.flush()
                        } else if (n < 0 && n != -2) {
                            break
                        }
                    }
                } catch (_: IOException) {
                }
            }

            localToRemote.join()
            remoteToLocal.cancel()
            pipeScope.cancel()
        } catch (_: Exception) {
            // handshake failed, channel open failed, or pipe IO error
        } finally {
            channel?.runCatching { close() }
            socket.runCatching { close() }
        }
    }

    /**
     * Writes a SOCKS5 reply packet with the given REP code and a dummy
     * `0.0.0.0:0` BND address (the SSH tunnel doesn't expose the real
     * bound address of the outgoing connection to us, and all common
     * clients ignore this field).
     */
    private fun writeSocksReply(output: DataOutputStream, rep: Int) {
        output.write(
            byteArrayOf(
                0x05.toByte(), // VER
                rep.toByte(),   // REP
                0x00.toByte(), // RSV
                0x01.toByte(), // ATYP = IPv4
                0x00, 0x00, 0x00, 0x00, // BND.ADDR
                0x00, 0x00,             // BND.PORT
            ),
        )
        output.flush()
    }

    private fun updateStatus(forwardId: String, state: ForwardState, error: String? = null) {
        _statuses.value = _statuses.value.toMutableMap().apply {
            put(forwardId, ForwardStatus(forwardId, state, error))
        }
    }
}
