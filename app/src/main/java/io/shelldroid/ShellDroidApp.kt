package io.shelldroid

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import io.shelldroid.service.session.SshSessionService

/**
 * Application class.
 *
 *  - `@HiltAndroidApp` enables constructor injection across the whole graph.
 *  - Creates the notification channel used by [SshSessionService] on startup.
 */
@HiltAndroidApp
class ShellDroidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                SshSessionService.CHANNEL_ID,
                SshSessionService.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Notificación persistente cuando hay sesiones SSH activas"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }
}
