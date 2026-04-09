package io.shelldroid.service.session

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
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
 *
 * Notifications include a content intent and a dedicated "Abrir terminal"
 * action. Both build a PendingIntent that launches `MainActivity` with
 * an extra string that [io.shelldroid.feature.terminal.TerminalLaunchRequest]
 * picks up and turns into a nav-host navigation to the terminal for the
 * given host. This is the "recover running session" path the user wants.
 */
@AndroidEntryPoint
class SshSessionService : LifecycleService() {

    @Inject lateinit var sessionManager: SshSessionManager

    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(sessionManager.activeCount()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
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
        if (intent?.action == ACTION_DISCONNECT_ALL) {
            sessionManager.disconnectAll()
            return START_NOT_STICKY
        }
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

    /**
     * Builds a PendingIntent that launches MainActivity and carries the
     * host id as an extra so the nav host can route directly to the
     * terminal for that host. Uses [packageManager.getLaunchIntentForPackage]
     * to avoid a direct class reference to `:app`.
     *
     * If [targetHostId] is null (notification about "0 active sessions"
     * or the first host cannot be determined), falls back to the plain
     * launch intent with no extra — the app opens on the host list.
     */
    private fun launchPendingIntent(targetHostId: String?): PendingIntent? {
        val base = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        base.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (targetHostId != null) {
            base.putExtra(EXTRA_HOST_ID, targetHostId)
        }
        // Encode the host id in the data URI as a secondary carrier so
        // multiple distinct pending intents can coexist — Android de-dupes
        // PendingIntents by (Intent, flags) and plain extras don't count.
        val suffix = targetHostId?.let { "-$it" } ?: ""
        val requestCode = (targetHostId?.hashCode() ?: 0)
        return PendingIntent.getActivity(
            this,
            requestCode,
            base,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildNotification(active: Int): Notification {
        val pluralEs = if (active == 1) "" else "es"
        val pluralActiva = if (active == 1) "" else "s"
        val title = if (active == 0) "ShellDroid" else "ShellDroid — $active sesión$pluralEs"
        val text = if (active == 0) {
            "Sin sesiones activas"
        } else {
            "$active sesión$pluralEs SSH activa$pluralActiva · tocá para abrir"
        }

        val firstHostId = sessionManager.activeHostIds().firstOrNull()
        val contentPi = launchPendingIntent(firstHostId)
        val actionPi = launchPendingIntent(firstHostId)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(io.shelldroid.service.session.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (contentPi != null) builder.setContentIntent(contentPi)
        if (actionPi != null && firstHostId != null) {
            builder.addAction(
                android.R.drawable.ic_menu_view,
                "Abrir terminal",
                actionPi,
            )
        }

        // "Desconectar" action — sends ACTION_DISCONNECT_ALL to this
        // service, which calls sessionManager.disconnectAll() and the
        // activeCountFlow drops to 0, stopping the service + notification.
        val disconnectIntent = Intent(this, SshSessionService::class.java).apply {
            action = ACTION_DISCONNECT_ALL
        }
        val disconnectPi = PendingIntent.getService(
            this, 0, disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Desconectar",
            disconnectPi,
        )

        return builder.build()
    }

    companion object {
        const val CHANNEL_ID = "ssh_sessions"
        const val CHANNEL_NAME = "Sesiones SSH activas"
        const val NOTIF_ID = 1001
        const val ACTION_DISCONNECT_ALL = "io.shelldroid.DISCONNECT_ALL"

        /**
         * String extra key used to deep-link a notification tap into the
         * terminal screen for a specific host. Mirrored from
         * `io.shelldroid.feature.terminal.TerminalLaunchRequest.EXTRA_HOST_ID`
         * (duplicated here to avoid `:service:session` depending on
         * `:feature:terminal`).
         */
        const val EXTRA_HOST_ID: String = "io.shelldroid.extra.HOST_ID"
    }
}
