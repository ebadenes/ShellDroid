package io.shelldroid.core.ssh

import io.shelldroid.core.ssh.model.AuthMethod
import io.shelldroid.core.ssh.model.SshConfig
import io.shelldroid.ssh.native_.LibSsh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Idiomatic Kotlin wrapper around the libssh JNI bindings.
 *
 * Thread safety:
 * - One [LibSshClient] manages exactly one libssh session.
 * - Writes are serialized through [writeMutex]; reads on a [ShellChannel]
 *   may run concurrently with writes but two readers on the same fd are not
 *   safe — coordinate at the caller side.
 *
 * Lifecycle: callers MUST call [disconnect] when done. The class does not
 * implement a finalizer — leaking the native pointer leaks an FD.
 *
 * Credential handling: passwords/passphrases are passed as `CharArray`. The
 * client converts them to UTF-8 bytes for the JNI call and zeroizes the
 * intermediate ByteArray in `finally`. Callers retain ownership of the
 * source `CharArray` and are responsible for clearing it themselves.
 */
class LibSshClient internal constructor() {

    @Volatile private var sessionPtr: Long = 0L

    /**
     * Gates ALL libssh operations on this session. libssh 0.11 is not
     * safe for concurrent operations on the same session: reads, writes,
     * channel open/close, keepalives etc. all manipulate the same packet
     * state machine. Running any two simultaneously corrupts the AEAD
     * sequence counter and produces "Packet len too high" on subsequent
     * decryptions.
     */
    internal val sessionMutex = Mutex()

    /**
     * Allocates a libssh session and runs `ssh_connect`. Does NOT authenticate
     * — call [verifyHostKey] then [authenticate] after.
     *
     * Returns a `Result` so the caller can pattern-match on
     * [SshConnectException] subclasses.
     */
    suspend fun connect(config: SshConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val s = LibSsh.nativeNewSession()
            if (s == 0L) throw SshConnectException.Unknown("ssh_new() returned null")
            sessionPtr = s

            check(LibSsh.nativeSetOptionString(s, "host", config.hostname) == LibSsh.SSH_OK) {
                "set host failed: ${LibSsh.nativeGetError(s)}"
            }
            check(LibSsh.nativeSetOptionInt(s, "port", config.port) == LibSsh.SSH_OK) {
                "set port failed: ${LibSsh.nativeGetError(s)}"
            }
            check(LibSsh.nativeSetOptionString(s, "user", config.username) == LibSsh.SSH_OK) {
                "set user failed: ${LibSsh.nativeGetError(s)}"
            }
            check(LibSsh.nativeSetOptionInt(s, "timeout", config.timeoutSec) == LibSsh.SSH_OK) {
                "set timeout failed: ${LibSsh.nativeGetError(s)}"
            }

            val rc = runInterruptible { LibSsh.nativeConnect(s) }
            if (rc != LibSsh.SSH_OK) {
                val msg = LibSsh.nativeGetError(s)
                throw SshConnectException.NetworkError(RuntimeException(msg))
            }
        }.onFailure {
            disconnect()
        }
    }

    /** Returns the server public key blob (raw bytes), or null if unavailable. */
    fun getServerPublicKeyBlob(): ByteArray? {
        require(sessionPtr != 0L) { "not connected" }
        return LibSsh.nativeGetServerPublicKey(sessionPtr)
    }

    /** Returns the host-key algorithm name, e.g. `"ssh-ed25519"`. */
    fun getPublicKeyType(blob: ByteArray): String =
        LibSsh.nativeGetPublicKeyType(blob)

    /**
     * Convenience: pulls the server key + type and delegates to [verifier].
     * Returns `true` if the host is trusted; `false` if rejected.
     */
    suspend fun verifyHostKey(
        verifier: TofuHostKeyVerifier,
        hostname: String,
        port: Int,
    ): Boolean {
        val blob = getServerPublicKeyBlob()
            ?: throw SshConnectException.Unknown("server did not present a public key")
        val type = getPublicKeyType(blob)
        return verifier.verify(hostname, port, type, blob)
    }

    /**
     * Authenticates against the connected session.
     *
     * For [AuthMethod.Password], the password `CharArray` is converted to a
     * UTF-8 `ByteArray` that is zeroized after the JNI call. The caller's
     * `CharArray` is NOT modified.
     */
    suspend fun authenticate(auth: AuthMethod, username: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val s = sessionPtr
                require(s != 0L) { "not connected" }
                val rc = when (auth) {
                    is AuthMethod.Password -> {
                        val bytes = charArrayToUtf8Bytes(auth.password)
                        try {
                            runInterruptible { LibSsh.nativeAuthPassword(s, username, bytes) }
                        } finally {
                            bytes.fill(0)
                        }
                    }
                    is AuthMethod.PublicKey -> {
                        val passBytes = auth.passphrase?.let { charArrayToUtf8Bytes(it) }
                        try {
                            runInterruptible {
                                LibSsh.nativeAuthPrivateKey(s, username, auth.privateKeyPem, passBytes)
                            }
                        } finally {
                            passBytes?.fill(0)
                        }
                    }
                }
                if (rc != LibSsh.SSH_OK) {
                    throw SshConnectException.AuthFailed(username, auth.methodName)
                }
            }
        }

    /**
     * Opens a shell channel with a PTY. Suspending because each step
     * (channel_open, request_pty, request_shell) does a network round
     * trip. The returned [ShellChannel] shares the [sessionMutex] so
     * reads, writes, resize and close all serialize on the same lock.
     */
    suspend fun openShell(cols: Int, rows: Int, term: String = "xterm-256color"): ShellChannel =
        sessionMutex.withLock {
            withContext(Dispatchers.IO) {
                val s = sessionPtr
                require(s != 0L) { "not connected" }
                val ch = LibSsh.nativeNewChannel(s)
                if (ch == 0L) throw SshConnectException.Unknown("ssh_channel_new() returned null")

                if (LibSsh.nativeOpenSession(ch) != LibSsh.SSH_OK) {
                    LibSsh.nativeChannelFree(ch)
                    throw SshConnectException.Unknown("open_session failed: ${LibSsh.nativeGetError(s)}")
                }
                if (LibSsh.nativeRequestPty(ch, term, cols, rows) != LibSsh.SSH_OK) {
                    LibSsh.nativeChannelClose(ch)
                    LibSsh.nativeChannelFree(ch)
                    throw SshConnectException.Unknown("request_pty failed: ${LibSsh.nativeGetError(s)}")
                }
                if (LibSsh.nativeShell(ch) != LibSsh.SSH_OK) {
                    LibSsh.nativeChannelClose(ch)
                    LibSsh.nativeChannelFree(ch)
                    throw SshConnectException.Unknown("request_shell failed: ${LibSsh.nativeGetError(s)}")
                }
                ShellChannel(ch, sessionMutex)
            }
        }

    /** Returns the last libssh error string, or null if not connected. */
    fun lastError(): String? =
        if (sessionPtr == 0L) null else LibSsh.nativeGetError(sessionPtr)

    /** Disconnects and frees the underlying session. Idempotent. */
    fun disconnect() {
        val s = sessionPtr
        if (s != 0L) {
            sessionPtr = 0L
            try { LibSsh.nativeDisconnect(s) } catch (_: Throwable) {}
            try { LibSsh.nativeFreeSession(s) } catch (_: Throwable) {}
        }
    }

    private fun charArrayToUtf8Bytes(chars: CharArray): ByteArray {
        // Avoid going through String to keep secrets out of the String pool.
        val cb = java.nio.CharBuffer.wrap(chars)
        val bb = Charsets.UTF_8.encode(cb)
        val out = ByteArray(bb.remaining())
        bb.get(out)
        // zeroize the ByteBuffer's backing array if accessible
        if (bb.hasArray()) {
            val backing = bb.array()
            for (i in backing.indices) backing[i] = 0
        }
        return out
    }
}

/**
 * A live shell channel with PTY allocated. Reads block until data is
 * available; resize and write are non-blocking.
 *
 * Lifecycle: call [close] when done. After close the underlying pointer
 * is invalid.
 */
class ShellChannel internal constructor(
    private var channelPtr: Long,
    private val sessionMutex: Mutex,
) {
    /**
     * Polling read with a timeout in milliseconds. Returns bytes read, 0 on
     * EOF, -2 (SSH_AGAIN) on timeout, -1 on error. The mutex is held only
     * for the duration of a single native call so writers can slip in
     * between polls. Keep [timeoutMs] small (<=100 ms) to bound write
     * latency when the reader is idle.
     */
    suspend fun readStdoutTimeout(buf: ByteArray, timeoutMs: Int): Int =
        sessionMutex.withLock {
            withContext(Dispatchers.IO) {
                if (channelPtr == 0L) -1
                else LibSsh.nativeChannelReadTimeout(channelPtr, buf, 0, timeoutMs)
            }
        }

    /** Serialized write of `len` bytes from `data` starting at `off`. */
    suspend fun write(data: ByteArray, off: Int = 0, len: Int = data.size): Int =
        sessionMutex.withLock {
            withContext(Dispatchers.IO) {
                if (channelPtr == 0L) -1
                else LibSsh.nativeChannelWrite(channelPtr, data, off, len)
            }
        }

    /**
     * Suspending resize that serializes with reads/writes. The pty
     * window-change packet is a full network round trip.
     */
    suspend fun resize(cols: Int, rows: Int) {
        sessionMutex.withLock {
            withContext(Dispatchers.IO) {
                if (channelPtr != 0L) LibSsh.nativeChangePtySize(channelPtr, cols, rows)
            }
        }
    }

    /**
     * Suspending close that serializes with any in-flight read/write on
     * the same session. After this returns the channel pointer is 0 and
     * subsequent read/write calls short-circuit to -1.
     */
    suspend fun close() {
        sessionMutex.withLock {
            withContext(Dispatchers.IO) {
                val c = channelPtr
                if (c != 0L) {
                    channelPtr = 0L
                    try { LibSsh.nativeChannelClose(c) } catch (_: Throwable) {}
                    try { LibSsh.nativeChannelFree(c) } catch (_: Throwable) {}
                }
            }
        }
    }
}
