package io.shelldroid.feature.terminal

import io.shelldroid.core.ssh.SshSessionManager
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide registry of [TerminalBridge] instances, keyed by host id.
 *
 * Bridges live HERE, not inside [TerminalViewModel]. The VM is the
 * transient presenter for the Compose screen; the bridge is the
 * long-lived owner of the libssh shell channel, the reader/writer
 * coroutines and the [org.connectbot.terminal.TerminalEmulator]. When
 * the user hits back on the terminal screen the VM is destroyed, but
 * the bridge keeps running and holds the warm session state, so
 * reconnecting is instantaneous and never triggers the BR1 race (which
 * was the reader/writer being torn down mid-native-call whenever the
 * VM's onCleared fired).
 *
 * The SSH [SshSessionManager] continues to own the libssh session
 * itself. The registry is orthogonal: multiple bridges on the same
 * hostId are deduplicated; the registry is the single source of truth
 * for "is there currently a live terminal session for this host?".
 */
@Singleton
class TerminalBridgeRegistry @Inject constructor(
    private val sessionManager: SshSessionManager,
) {
    private val bridges = ConcurrentHashMap<String, TerminalBridge>()

    /**
     * Return the existing bridge for [hostId] if any, otherwise create
     * and register a new one. Does NOT call `attach()` on the returned
     * bridge — the caller must invoke attach() with the screen's initial
     * grid dimensions. If the bridge already existed and was attached,
     * a second attach is a no-op (see [TerminalBridge.attach]).
     */
    fun getOrCreate(hostId: String): TerminalBridge {
        return bridges.getOrPut(hostId) { TerminalBridge(sessionManager) }
    }

    /** Returns `true` if a bridge already exists for [hostId]. */
    fun has(hostId: String): Boolean = bridges.containsKey(hostId)

    /**
     * Get the bridge for [hostId] without creating one if absent.
     */
    fun peek(hostId: String): TerminalBridge? = bridges[hostId]

    /**
     * Tear down the bridge for [hostId] and remove it from the registry.
     * Use this when the user explicitly disconnects.
     *
     * The actual teardown ([TerminalBridge.detach]) is fully async on
     * the bridge's own IO scope, so this call never blocks the caller.
     * [disconnectSession] is forwarded to the bridge: `true` fully
     * disconnects the libssh session via [SshSessionManager], `false`
     * only closes the shell channel and leaves the session warm (not
     * usually what you want when explicitly releasing; prefer `true`).
     */
    fun release(hostId: String, disconnectSession: Boolean = true) {
        val bridge = bridges.remove(hostId) ?: return
        bridge.detach(disconnectSession)
    }

    /** All currently registered host ids. */
    fun activeHostIds(): Set<String> = bridges.keys.toSet()

    /** Count of live bridges. Used by the foreground service notification. */
    fun count(): Int = bridges.size
}
