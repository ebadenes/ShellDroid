package io.shelldroid.core.ssh

/**
 * Hook used by [SshSessionManager] to ensure the foreground service that
 * keeps SSH sessions alive while the app is in background is running.
 *
 * Lives in `:core:ssh` (instead of `:service:session`) to invert the
 * dependency: the service module depends on `:core:ssh`, not the other
 * way around. The concrete implementation is provided by `:service:session`
 * via Hilt.
 *
 * Tests can pass [NoopSessionServiceController] to avoid touching Android.
 */
interface SessionServiceController {
    /**
     * Ensure the foreground service is started. Idempotent: calling this
     * while the service is already running is a no-op (Android coalesces
     * `startForegroundService` intents).
     */
    fun ensureRunning()
}

/** No-op implementation for unit tests and pure-JVM contexts. */
object NoopSessionServiceController : SessionServiceController {
    override fun ensureRunning() = Unit
}
