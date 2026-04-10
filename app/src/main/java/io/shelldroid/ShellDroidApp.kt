package io.shelldroid

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.HiltAndroidApp
import io.shelldroid.core.security.AutoLockObserver
import io.shelldroid.service.session.SshSessionService
import javax.inject.Inject

/**
 * Application class.
 *
 *  - `@HiltAndroidApp` enables constructor injection across the whole graph.
 *  - Registers [AutoLockObserver] with the process lifecycle for PIN lock.
 *  - Creates the notification channel used by [SshSessionService] on startup.
 */
@HiltAndroidApp
class ShellDroidApp : Application() {

    @Inject lateinit var autoLockObserver: AutoLockObserver

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(autoLockObserver)
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
