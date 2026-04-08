package io.shelldroid.core.ssh

import com.google.common.truth.Truth.assertThat
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Smoke test: proves the embedded MINA SSHD fixture boots and can accept
 * a real SSH client connection. Uses mwiede/jsch as a pure-Java client so
 * we don't need any native code.
 *
 * This does NOT exercise our LibSshClient — that path requires the libssh
 * .so and Android system libraries and cannot run in pure JVM tests. It's
 * a fixture sanity check.
 */
class MinaSshdSmokeTest : SshIntegrationTestBase() {

    @Test
    fun `connect authenticate and exec echo via jsch`() {
        val jsch = JSch()
        val session = jsch.getSession(user, "127.0.0.1", port).apply {
            setPassword("test")
            setConfig("StrictHostKeyChecking", "no")
            connect(5_000)
        }
        try {
            assertThat(session.isConnected).isTrue()

            val channel = session.openChannel("exec") as ChannelExec
            val stdout = ByteArrayOutputStream()
            channel.setCommand("echo hola")
            channel.outputStream = stdout // redundant but harmless on exec
            val input = channel.inputStream
            channel.connect(5_000)

            val buf = ByteArray(1024)
            val collected = ByteArrayOutputStream()
            while (true) {
                while (input.available() > 0) {
                    val n = input.read(buf)
                    if (n < 0) break
                    collected.write(buf, 0, n)
                }
                if (channel.isClosed) {
                    if (input.available() > 0) continue
                    break
                }
                Thread.sleep(50)
            }
            channel.disconnect()

            val text = collected.toString(Charsets.UTF_8)
            assertThat(text).contains("hola")
        } finally {
            session.disconnect()
        }
    }
}
