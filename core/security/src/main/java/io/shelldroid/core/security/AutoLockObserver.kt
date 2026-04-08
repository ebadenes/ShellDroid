package io.shelldroid.core.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes the [androidx.lifecycle.ProcessLifecycleOwner] so the lock state
 * can update when the app moves between foreground and background.
 *
 * No state is kept here — [LockManager.isLocked] re-checks the persisted
 * `lastUnlock` timestamp on demand. We keep this observer as the integration
 * point for future logic (e.g. clearing in-memory caches on background).
 *
 * **Registration**: this observer must be registered from `ShellDroidApp.onCreate()`
 * in `:app` via:
 * ```kotlin
 * ProcessLifecycleOwner.get().lifecycle.addObserver(autoLockObserver)
 * ```
 * `:core:security` cannot register itself because it has no entry point in
 * the application lifecycle.
 */
@Singleton
class AutoLockObserver @Inject constructor(
    @Suppress("unused") private val lockManager: LockManager
) : DefaultLifecycleObserver {

    override fun onStop(owner: LifecycleOwner) {
        // App went to background. Nothing to persist: LockManager.isLocked()
        // compares the timestamp on the next foreground check.
    }

    override fun onStart(owner: LifecycleOwner) {
        // The hosting Activity is responsible for calling LockManager.isLocked()
        // and routing to LockActivity if needed.
    }
}
