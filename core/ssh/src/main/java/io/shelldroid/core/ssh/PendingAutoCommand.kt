package io.shelldroid.core.ssh

import java.util.concurrent.ConcurrentHashMap

/**
 * Simple in-memory store for auto-commands that should execute once a
 * shell channel opens for a given host. Populated by the hosts module
 * on connect; consumed (and removed) by the terminal module on attach.
 */
object PendingAutoCommand {
    private val pending = ConcurrentHashMap<String, String>()

    /** Enqueue an auto-command for [hostId]. */
    fun set(hostId: String, command: String) {
        if (command.isNotBlank()) pending[hostId] = command
    }

    /** Retrieve and remove the pending auto-command for [hostId], or null. */
    fun take(hostId: String): String? = pending.remove(hostId)
}
