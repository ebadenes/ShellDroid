package io.shelldroid.feature.terminal

import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalViewClient

/**
 * Our [TerminalViewClient] implementation for [com.termux.view.TerminalView].
 *
 * Responsibilities:
 *  - Relay "single tap on the terminal" back to the Compose layer so the
 *    soft keyboard can be brought back up after the user dismisses it
 *    (we cannot use `View.setOnClickListener` reliably because
 *    `TerminalView.onTouchEvent` does not call `performClick`; but its
 *    internal `GestureDetector` always calls us on single tap).
 *  - Hold sticky one-shot Ctrl / Alt state for the extended key bar.
 *    Termux reads `readControlKey()` / `readAltKey()` on every code-point
 *    input, so we return `true` once after the user taps Ctrl/Alt and
 *    then auto-clear. The next regular keystroke gets the modifier
 *    applied via Termux's own `inputCodePoint` transform.
 */
class ShellDroidTerminalViewClient(
    private val onTap: () -> Unit = {},
) : TerminalViewClient {

    @Volatile private var ctrlSticky: Boolean = false
    @Volatile private var altSticky: Boolean = false

    fun setCtrlSticky(enabled: Boolean) { ctrlSticky = enabled }
    fun setAltSticky(enabled: Boolean) { altSticky = enabled }
    fun isCtrlSticky(): Boolean = ctrlSticky
    fun isAltSticky(): Boolean = altSticky

    override fun onScale(scale: Float): Float = scale

    override fun onSingleTapUp(e: MotionEvent) {
        onTap()
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = false
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) { /* no-op */ }

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        Log.d("ShellTVC", "onKeyDown keyCode=$keyCode unicode=${e.unicodeChar}")
        return false
    }
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false

    /**
     * Returned once after the user taps the Ctrl button, then cleared.
     * Termux's `inputCodePoint` queries this per code-point and applies
     * the Ctrl transform accordingly.
     */
    override fun readControlKey(): Boolean {
        val v = ctrlSticky
        if (v) ctrlSticky = false
        return v
    }

    override fun readAltKey(): Boolean {
        val v = altSticky
        if (v) altSticky = false
        return v
    }

    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        Log.d("ShellTVC", "onCodePoint cp=$codePoint ctrlDown=$ctrlDown")
        return false
    }

    override fun onEmulatorSet() { /* no-op */ }

    override fun logError(tag: String?, message: String?) { /* no-op */ }
    override fun logWarn(tag: String?, message: String?) { /* no-op */ }
    override fun logInfo(tag: String?, message: String?) { /* no-op */ }
    override fun logDebug(tag: String?, message: String?) { /* no-op */ }
    override fun logVerbose(tag: String?, message: String?) { /* no-op */ }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { /* no-op */ }
    override fun logStackTrace(tag: String?, e: Exception?) { /* no-op */ }
}
