package io.shelldroid.feature.terminal

import io.shelldroid.core.ssh.ShellChannel

/**
 * Thin abstraction over a remote shell I/O endpoint so [SshTerminalSession]
 * can be unit-tested without touching the `internal` [ShellChannel] ctor.
 *
 * Every operation is suspend because the real libssh-backed implementation
 * serializes reads, writes, resize and close through a session-wide mutex
 * (libssh 0.11 is not safe for concurrent session operations).
 */
interface TerminalIo {
    suspend fun write(data: ByteArray, off: Int, len: Int): Int
    suspend fun resize(cols: Int, rows: Int)
    suspend fun close()
}

/** Production adapter that forwards to a real [ShellChannel]. */
class ShellChannelTerminalIo(private val ch: ShellChannel) : TerminalIo {
    override suspend fun write(data: ByteArray, off: Int, len: Int): Int =
        ch.write(data, off, len)

    override suspend fun resize(cols: Int, rows: Int) {
        ch.resize(cols, rows)
    }

    override suspend fun close() {
        ch.close()
    }
}
