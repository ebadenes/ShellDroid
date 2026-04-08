package io.shelldroid.feature.terminal

import android.view.KeyEvent

/**
 * Process-wide hook so the host [android.app.Activity] can route physical
 * volume-up / volume-down (and eventually any other hardware) key events
 * to whichever screen is currently interested in them.
 *
 * `TerminalScreen` installs a handler on entry and clears it on exit via
 * `DisposableEffect`. `MainActivity.dispatchKeyEvent` reads [handler] on
 * every key event and, if non-null, lets the handler consume the event.
 *
 * Kept as a plain singleton rather than a CompositionLocal because the
 * consumer (the host Activity) lives outside the Compose tree and
 * dispatches key events BEFORE Compose has a chance to see them.
 */
object HardwareKeyInterceptor {
    @Volatile
    var handler: ((keyCode: Int, event: KeyEvent) -> Boolean)? = null
}
