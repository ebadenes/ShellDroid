package io.shelldroid.ssh.native_

/**
 * Low-level JNI bindings around libssh.
 *
 * Pointer values (`sessionPtr`, `channelPtr`) are opaque longs holding native
 * pointers. 0 means null. Return codes follow libssh: SSH_OK = 0, SSH_ERROR = -1,
 * SSH_AGAIN = -2.
 *
 * No object here owns lifecycle — callers must pair `nativeNewSession` with
 * `nativeFreeSession`, and `nativeNewChannel` with `nativeChannelFree`.
 */
object LibSsh {
    const val SSH_OK = 0
    const val SSH_ERROR = -1
    const val SSH_AGAIN = -2

    init {
        System.loadLibrary("shelldroid_ssh")
    }

    @JvmStatic external fun nativeVersion(): String

    // session lifecycle
    @JvmStatic external fun nativeNewSession(): Long
    @JvmStatic external fun nativeFreeSession(sessionPtr: Long)

    // options
    @JvmStatic external fun nativeSetOptionString(sessionPtr: Long, option: String, value: String?): Int
    @JvmStatic external fun nativeSetOptionInt(sessionPtr: Long, option: String, value: Int): Int

    // connection
    @JvmStatic external fun nativeConnect(sessionPtr: Long): Int
    @JvmStatic external fun nativeDisconnect(sessionPtr: Long)
    @JvmStatic external fun nativeGetError(sessionPtr: Long): String

    // host key
    @JvmStatic external fun nativeGetServerPublicKey(sessionPtr: Long): ByteArray?
    @JvmStatic external fun nativeGetPublicKeyType(keyBlob: ByteArray): String
    @JvmStatic external fun nativeGetPublicKeyHashSha256(keyBlob: ByteArray): ByteArray?

    // auth
    @JvmStatic external fun nativeAuthPassword(sessionPtr: Long, username: String, passwordBytes: ByteArray): Int
    @JvmStatic external fun nativeAuthPrivateKey(sessionPtr: Long, username: String, privateKeyPem: ByteArray, passphraseBytes: ByteArray?): Int

    // channel
    @JvmStatic external fun nativeNewChannel(sessionPtr: Long): Long
    @JvmStatic external fun nativeOpenSession(channelPtr: Long): Int
    @JvmStatic external fun nativeRequestPty(channelPtr: Long, term: String, cols: Int, rows: Int): Int
    @JvmStatic external fun nativeChangePtySize(channelPtr: Long, cols: Int, rows: Int): Int
    @JvmStatic external fun nativeShell(channelPtr: Long): Int
    @JvmStatic external fun nativeChannelRead(channelPtr: Long, buffer: ByteArray, isStderr: Int): Int
    @JvmStatic external fun nativeChannelReadTimeout(channelPtr: Long, buffer: ByteArray, isStderr: Int, timeoutMs: Int): Int
    @JvmStatic external fun nativeChannelReadNonblocking(channelPtr: Long, buffer: ByteArray, isStderr: Int): Int
    @JvmStatic external fun nativeChannelIsEof(channelPtr: Long): Int
    @JvmStatic external fun nativeChannelWrite(channelPtr: Long, data: ByteArray, offset: Int, length: Int): Int
    @JvmStatic external fun nativeChannelClose(channelPtr: Long)
    @JvmStatic external fun nativeChannelFree(channelPtr: Long)
}
