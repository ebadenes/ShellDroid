package io.shelldroid.feature.terminal

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SshTerminalSessionTest {

    private class FakeIo : TerminalIo {
        val writes = mutableListOf<ByteArray>()
        var closed = false
        var lastResize: Pair<Int, Int>? = null
        override suspend fun write(data: ByteArray, off: Int, len: Int): Int {
            writes.add(data.copyOfRange(off, off + len))
            return len
        }
        override suspend fun resize(cols: Int, rows: Int) { lastResize = cols to rows }
        override suspend fun close() { closed = true }
    }

    private fun newSession(
        io: TerminalIo,
        scope: TestScope,
        executorRecorder: MutableList<Runnable> = mutableListOf(),
    ): SshTerminalSession = SshTerminalSession(
        io = io,
        ioScope = scope,
        mainExecutor = { r -> executorRecorder.add(r); r.run() },
        client = NoOpTerminalSessionClient(),
    )

    @Test
    fun `write forwards bytes to TerminalIo`() = runTest(UnconfinedTestDispatcher()) {
        val io = FakeIo()
        val session = newSession(io, this)
        val payload = "hello".toByteArray()

        session.write(payload, 0, payload.size)

        assertThat(io.writes).hasSize(1)
        assertThat(io.writes[0]).isEqualTo(payload)
    }

    @Test
    fun `write copies the slice respecting offset and count`() =
        runTest(UnconfinedTestDispatcher()) {
            val io = FakeIo()
            val session = newSession(io, this)
            val payload = "abcdef".toByteArray()

            session.write(payload, 2, 3)

            assertThat(io.writes).hasSize(1)
            assertThat(io.writes[0]).isEqualTo("cde".toByteArray())
        }

    @Test
    fun `closeChannel closes io and marks session not running`() =
        runTest(UnconfinedTestDispatcher()) {
            val io = FakeIo()
            val session = newSession(io, this)
            session.initializeEmulator(80, 24, 12, 24)
            assertThat(session.isRunning).isTrue()

            session.closeChannel()

            assertThat(io.closed).isTrue()
            assertThat(session.isRunning).isFalse()
        }

    @Test
    fun `feedFromShell dispatches via mainExecutor`() =
        runTest(UnconfinedTestDispatcher()) {
            val io = FakeIo()
            val recorded = mutableListOf<Runnable>()
            val session = newSession(io, this, recorded)
            session.initializeEmulator(80, 24, 12, 24)

            val data = "x".toByteArray()
            session.feedFromShell(data, data.size)

            assertThat(recorded).hasSize(1)
        }

    @Test
    fun `feedFromShell with zero length is a no-op`() =
        runTest(UnconfinedTestDispatcher()) {
            val io = FakeIo()
            val recorded = mutableListOf<Runnable>()
            val session = newSession(io, this, recorded)

            session.feedFromShell(ByteArray(0), 0)

            assertThat(recorded).isEmpty()
        }
}
