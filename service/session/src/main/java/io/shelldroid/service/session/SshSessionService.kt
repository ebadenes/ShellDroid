package io.shelldroid.service.session

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.shelldroid.core.ssh.SshSessionManager
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps SSH sessions alive while the app is in
 * background. Lifecycle is driven by [SshSessionManager.activeCountFlow]:
 *
 *  - on create → call [startForeground] with the initial notification
 *  - on each emission → update the notification (count changes) or stop
 *    the service when the count reaches zero
 *  - [onTaskRemoved] does NOT stop the service: sessions outlive the task
 *
 * The service is started by [SessionServiceControllerImpl.ensureRunning],
 * which is invoked from [SshSessionManager.connect]. Once running, it
 * self-manages via the flow.
 */
@AndroidEntryPoint
class SshSessionService : LifecycleService() {

    @Inject lateinit var sessionManager: SshSessionManager

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification(sessionManager.activeCount()))
        lifecycleScope.launch {
            sessionManager.activeCountFlow.collect { count ->
                if (count == 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    stopSelf()
                } else {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIF_ID, buildNotification(count))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    /**
     * Policy: do NOT stop the service when the user swipes the task away.
     * Active SSH sessions remain alive while the process exists. If Android
     * eventually kills the process the sessions are lost — that is expected.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    private fun buildNotification(active: Int): Notification {
        val pluralEs = if (active == 1) "" else "es"
        val pluralActiva = if (active == 1) "" else "s"
        val title = if (active == 0) "ShellDroid" else "ShellDroid — $active sesión$pluralEs"
        val text = if (active == 0) {
            "Sin sesiones activas"
        } else {
            "$active sesión$pluralEs SSH activa$pluralActiva"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync) // TODO polish: ícono propio
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "ssh_sessions"
        const val CHANNEL_NAME = "Sesiones SSH activas"
        const val NOTIF_ID = 1001
    }
}
