package io.shelldroid

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import io.shelldroid.service.session.SshSessionService

/**
 * Application class. Registers the notification channel used by
 * [SshSessionService].
 *
 * NOTE: this class is intentionally NOT annotated with `@HiltAndroidApp`
 * yet. The Hilt graph currently has two pre-existing collisions that
 * surface only when a `@HiltAndroidApp` triggers whole-graph validation:
 *
 *  1. `HostKeyPrompter` has no provider (expected to come from
 *     `:feature:hosts`, which is still a UI skeleton).
 *  2. `:core:db` and `:core:security` both `@Provides` an unqualified
 *     `DataStore<Preferences>` — the second one needs a Hilt qualifier
 *     to disambiguate the two stores.
 *
 * Both of those are pre-existing issues outside the scope of phase 6 and
 * are flagged in the phase report. Once they are fixed, this class should
 * be annotated `@HiltAndroidApp` so injection into [SshSessionService]
 * works at runtime. Until then the service still compiles and the FGS
 * plumbing is in place; runtime injection will start working as soon as
 * the annotation is added.
 */
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
