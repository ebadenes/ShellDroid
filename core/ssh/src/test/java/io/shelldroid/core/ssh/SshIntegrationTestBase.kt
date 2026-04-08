package io.shelldroid.core.ssh

import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.Environment
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.junit.After
import org.junit.Before
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files

/**
 * Boots an embedded Apache MINA SSHD server on a random free localhost port
 * before each test and stops it after. Accepts user "test" with password
 * "test". Supports a minimal `exec` command factory: `echo <text>`, `pwd`,
 * or unknown → "unknown: <cmd>".
 *
 * This provides a network-real SSH target without needing a device or
 * external container. It does NOT exercise our libssh JNI path — that
 * requires the Android .so and cannot run in a pure JVM.
 */
abstract class SshIntegrationTestBase {
    protected lateinit var server: SshServer
    protected var port: Int = 0
    protected val user: String = "test"
    protected val password: CharArray get() = "test".toCharArray()

    @Before
    fun startSshd() {
        val hostKeyFile = Files.createTempFile("shelldroid-test-hostkey", ".ser")
        server = SshServer.setUpDefaultServer().apply {
            this.port = 0 // random free port
            keyPairProvider = SimpleGeneratorHostKeyProvider(hostKeyFile)
            passwordAuthenticator = PasswordAuthenticator { u, p, _ ->
                u == "test" && p == "test"
            }
            commandFactory = CommandFactory { _, cmd -> CannedCommand(cmd) }
            start()
        }
        port = server.port
    }

    @After
    fun stopSshd() {
        if (::server.isInitialized) {
            server.stop(true)
        }
    }

    private class CannedCommand(private val cmd: String) : Command {
        private var cb: ExitCallback? = null
        private var out: OutputStream? = null
        private var err: OutputStream? = null

        override fun setExitCallback(callback: ExitCallback) { cb = callback }
        override fun setOutputStream(o: OutputStream) { out = o }
        override fun setErrorStream(e: OutputStream) { err = e }
        override fun setInputStream(i: InputStream) {}
        override fun start(channel: ChannelSession, env: Environment) {
            val line = when {
                cmd == "pwd" -> "/home/test\n"
                cmd.startsWith("echo ") -> cmd.removePrefix("echo ") + "\n"
                else -> "unknown: $cmd\n"
            }
            out?.write(line.toByteArray())
            out?.flush()
            cb?.onExit(0)
        }
        override fun destroy(channel: ChannelSession) {}
    }
}
