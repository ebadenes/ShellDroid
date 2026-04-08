package io.shelldroid.feature.terminal

import org.connectbot.terminal.ModifierManager

/**
 * [ModifierManager] implementation for ShellDroid's terminal. Holds the
 * current sticky state of Ctrl / Alt / Shift so the extended key bar can
 * toggle them and the next keystroke delivered to the emulator gets the
 * modifier applied.
 *
 * [clearTransients] is called by the emulator after it consumes the
 * modifier for a single keystroke — matches JuiceSSH / ConnectBot
 * one-shot semantics.
 */
class ShellDroidModifierManager : ModifierManager {

    @Volatile private var ctrl: Boolean = false
    @Volatile private var alt: Boolean = false
    @Volatile private var shift: Boolean = false

    override fun isCtrlActive(): Boolean = ctrl
    override fun isAltActive(): Boolean = alt
    override fun isShiftActive(): Boolean = shift

    override fun clearTransients() {
        ctrl = false
        alt = false
        shift = false
    }

    fun toggleCtrl() { ctrl = !ctrl }
    fun toggleAlt() { alt = !alt }
    fun toggleShift() { shift = !shift }

    fun reset() {
        ctrl = false
        alt = false
        shift = false
    }
}
