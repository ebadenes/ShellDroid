package io.shelldroid.feature.terminal

import android.util.Log
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A [TerminalSession] whose I/O is backed by a remote SSH shell channel
 * instead of a local pty'd child process.
 *
 * The parent ctor only stores fields (verified in the vendored Termux source),
 * so passing nulls for shell path / args / env is safe — we never let the
 * parent try to spawn anything. We override [initializeEmulator] to construct
 * the emulator directly and mark [mShellPid] as alive so [isRunning] reports
 * `true`.
 *
 * Main-thread dispatching of emulator updates is parameterised via
 * [mainExecutor] so tests can pass a synchronous runner and avoid pulling in
 * `android.os.Handler`/`Looper` under pure JVM.
 */
class SshTerminalSession(
    private val io: TerminalIo,
    private val ioScope: CoroutineScope,
    private val mainExecutor: (Runnable) -> Unit,
    client: TerminalSessionClient,
) : TerminalSession(null, null, null, null, null, client) {

    override fun initializeEmulator(cols: Int, rows: Int, cellW: Int, cellH: Int) {
        mEmulator = TerminalEmulator(this, cols, rows, cellW, cellH, null, mClient)
        // Pseudo-alive pid so parent.isRunning() returns true. -1 == finished.
        mShellPid = 1
    }

    /**
     * Override the parent's [updateSize] to avoid its `JNI.setPtyWindowSize`
     * call, which triggers loading of `libtermux.so` (we exclude it from the
     * APK and never spawn a local pty anyway). We resize the emulator in
     * place and forward the new window dimensions to the remote via the
     * shell channel.
     */
    override fun updateSize(cols: Int, rows: Int, cellW: Int, cellH: Int) {
        val em = mEmulator
        if (em == null) {
            initializeEmulator(cols, rows, cellW, cellH)
            return
        }
        em.resize(cols, rows, cellW, cellH)
        // io.resize hits libssh via JNI and can block on a network round
        // trip for the pty window-change packet. Never run that on the
        // main thread — it froze the UI on screen unlock during on-device
        // testing.
        ioScope.launch(Dispatchers.IO) {
            try {
                io.resize(cols, rows)
            } catch (_: Throwable) {
                // channel may be closed mid-resize — ignore
            }
        }
    }

    override fun write(data: ByteArray?, offset: Int, count: Int) {
        if (data == null || count <= 0) return
        Log.d(TAG, "write(len=$count) bytes=${data.copyOfRange(offset, offset + count).toList()}")
        val copy = data.copyOfRange(offset, offset + count)
        ioScope.launch {
            try {
                val n = io.write(copy, 0, copy.size)
                Log.d(TAG, "io.write returned $n")
            } catch (_: CancellationException) {
                // swallow — scope cancelled
            } catch (t: Throwable) {
                Log.e(TAG, "io.write failed", t)
            }
        }
    }

    companion object { private const val TAG = "SshTerminalSession" }

    /**
     * Feed bytes received from the remote shell into the emulator.
     * Must end up on the main thread — dispatched via [mainExecutor].
     */
    fun feedFromShell(buf: ByteArray, length: Int) {
        if (length <= 0) return
        mainExecutor(Runnable {
            mEmulator?.append(buf, length)
            notifyScreenUpdate()
        })
    }

    /** No local child process to reap. */
    override fun finishIfRunning() {
        // no-op
    }

    /** Close the underlying channel and mark the session finished. */
    fun closeChannel() {
        try {
            io.close()
        } catch (_: Throwable) {
            // ignore
        }
        mShellPid = -1
    }
}
