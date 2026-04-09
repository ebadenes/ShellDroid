package io.shelldroid.service.session

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.shelldroid.core.ssh.SessionServiceController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete [SessionServiceController] that starts [SshSessionService] as a
 * foreground service via [ContextCompat.startForegroundService].
 *
 * Idempotent: subsequent invocations while the service is already running
 * are a no-op for the system (the service's `onStartCommand` runs again
 * with `START_STICKY` and updates state from the flow).
 */
@Singleton
class SessionServiceControllerImpl @Inject constructor(
    @ApplicationContext private val ctx: Context,
) : SessionServiceController {
    override fun ensureRunning() {
        Log.d("SessionSvcCtrl", "ensureRunning — starting SshSessionService")
        val intent = Intent(ctx, SshSessionService::class.java)
        ContextCompat.startForegroundService(ctx, intent)
    }
}
