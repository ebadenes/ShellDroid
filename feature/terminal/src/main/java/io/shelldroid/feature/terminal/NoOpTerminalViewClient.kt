package io.shelldroid.feature.terminal

import android.view.KeyEvent
import android.view.MotionEvent
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalViewClient

/**
 * Minimal [TerminalViewClient] so [com.termux.view.TerminalView] forwards
 * keyboard / IME input to the attached [TerminalSession]. Without a client
 * set, TerminalView silently drops input.
 *
 * All callbacks use sensible defaults: no char-based-input enforcement, no
 * ctrl-space workaround, no back-to-escape, no custom key handling. Returning
 * false from key/codePoint callbacks lets TerminalView fall back to the
 * default path which writes bytes into [TerminalSession].
 */
class NoOpTerminalViewClient : TerminalViewClient {

    override fun onScale(scale: Float): Float = scale

    override fun onSingleTapUp(e: MotionEvent) { /* no-op */ }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = false
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) { /* no-op */ }

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean = false
    override fun readAltKey(): Boolean = false
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false

    override fun onEmulatorSet() { /* no-op */ }

    override fun logError(tag: String?, message: String?) { /* no-op */ }
    override fun logWarn(tag: String?, message: String?) { /* no-op */ }
    override fun logInfo(tag: String?, message: String?) { /* no-op */ }
    override fun logDebug(tag: String?, message: String?) { /* no-op */ }
    override fun logVerbose(tag: String?, message: String?) { /* no-op */ }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { /* no-op */ }
    override fun logStackTrace(tag: String?, e: Exception?) { /* no-op */ }
}
