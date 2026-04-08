package io.shelldroid.core.ssh.model

/**
 * Connection parameters for an SSH session.
 *
 * @property hostId Stable identifier used as the key in [io.shelldroid.core.ssh.SshSessionManager].
 * @property hostname Resolvable hostname or IP literal.
 * @property port TCP port (default 22).
 * @property username Remote user.
 * @property auth Credential bundle.
 * @property timeoutSec Connect timeout in seconds.
 */
data class SshConfig(
    val hostId: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val auth: AuthMethod,
    val timeoutSec: Int = 30,
) {
    override fun toString(): String =
        "SshConfig(hostId=$hostId, host=$hostname:$port, user=$username, auth=***)"
}
