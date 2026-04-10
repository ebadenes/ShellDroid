package io.shelldroid.feature.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.connectbot.terminal.ModifierManager

/**
 * [ModifierManager] with Compose-observable state. When termlib calls
 * [clearTransients] after consuming a modifier keystroke, the Compose
 * state flips back and the hacker keyboard button automatically
 * un-highlights without manual polling.
 */
class ShellDroidModifierManager : ModifierManager {

    var ctrlActive by mutableStateOf(false)
        private set
    var altActive by mutableStateOf(false)
        private set
    var shiftActive by mutableStateOf(false)
        private set

    override fun isCtrlActive(): Boolean = ctrlActive
    override fun isAltActive(): Boolean = altActive
    override fun isShiftActive(): Boolean = shiftActive

    override fun clearTransients() {
        ctrlActive = false
        altActive = false
        shiftActive = false
    }

    fun toggleCtrl() { ctrlActive = !ctrlActive }
    fun toggleAlt() { altActive = !altActive }
    fun toggleShift() { shiftActive = !shiftActive }

    fun reset() {
        ctrlActive = false
        altActive = false
        shiftActive = false
    }
}
