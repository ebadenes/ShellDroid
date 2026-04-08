package io.shelldroid.feature.terminal

import io.shelldroid.core.ssh.ShellChannel

/**
 * Thin abstraction over a remote shell I/O endpoint so [SshTerminalSession]
 * can be unit-tested without touching the `internal` [ShellChannel] ctor.
 */
interface TerminalIo {
    suspend fun write(data: ByteArray, off: Int, len: Int): Int
    fun resize(cols: Int, rows: Int)
    fun close()
}

/** Production adapter that forwards to a real [ShellChannel]. */
class ShellChannelTerminalIo(private val ch: ShellChannel) : TerminalIo {
    override suspend fun write(data: ByteArray, off: Int, len: Int): Int =
        ch.write(data, off, len)

    override fun resize(cols: Int, rows: Int) {
        ch.resize(cols, rows)
    }

    override fun close() {
        ch.close()
    }
}
