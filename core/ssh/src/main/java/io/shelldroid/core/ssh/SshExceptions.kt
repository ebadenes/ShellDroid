package io.shelldroid.core.ssh

/**
 * Sealed hierarchy of all SSH connection-related failures.
 *
 * Catch the base class for a generic error path; pattern-match the
 * subclasses to drive UX (re-prompt password, alert user about MITM, etc).
 */
sealed class SshConnectException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    class HostKeyRejected(
        val hostname: String,
        val port: Int,
        val reason: String,
    ) : SshConnectException("Host key rejected for $hostname:$port — $reason")

    class HostKeyChanged(
        val hostname: String,
        val oldFingerprint: String,
        val newFingerprint: String,
    ) : SshConnectException(
        "Host key MISMATCH for $hostname — user must remove known_hosts entry manually " +
            "(old=$oldFingerprint, new=$newFingerprint)"
    )

    class AuthFailed(
        val username: String,
        val methodName: String,
    ) : SshConnectException("Auth failed for $username via $methodName")

    class NetworkError(cause: Throwable) :
        SshConnectException("Network error: ${cause.message}", cause)

    class Unknown(message: String, cause: Throwable? = null) :
        SshConnectException(message, cause)
}
