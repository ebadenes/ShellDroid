package io.shelldroid.feature.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.VTermKey

/**
 * Horizontally scrollable row of extra keys on top of the soft keyboard.
 * Matches the ConnectBot / JuiceSSH "hacker keyboard" pattern:
 * ESC, TAB, CTRL, ALT, arrows, HOME/END, PGUP/PGDN, symbols.
 *
 * Each tap either:
 *  - toggles a sticky modifier in [modifierManager] (CTRL, ALT), OR
 *  - dispatches a [VTermKey] straight to the emulator (arrows, F keys,
 *    Home/End), OR
 *  - dispatches a single character via [TerminalEmulator.dispatchCharacter]
 *    (symbols like `/`, `|`, `-`, `~`).
 *
 * The [onRequestShowKeyboard] callback is wired to re-open the soft
 * IME when the user taps the ⌨ button, giving a reliable recovery
 * path if the IME was dismissed.
 */
@Composable
fun TerminalKeyBar(
    emulator: TerminalEmulator,
    modifierManager: ShellDroidModifierManager,
    background: Color,
    foreground: Color,
    onRequestShowKeyboard: () -> Unit,
    onRequestSnippets: () -> Unit,
    onPaste: () -> Unit = {},
    onCopyAll: () -> Unit = {},
    onClear: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Recomposition trigger for sticky highlights. We mirror the
    // modifierManager state into local state so the buttons repaint
    // when the user taps CTRL / ALT.
    var ctrlOn by remember { mutableStateOf(false) }
    var altOn by remember { mutableStateOf(false) }

    val scroll = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .horizontalScroll(scroll)
            .padding(horizontal = 3.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyBarButton("ESC", foreground, false) {
            emulator.dispatchKey(0, VTermKey.ESCAPE)
        }
        KeyBarButton("TAB", foreground, false) {
            emulator.dispatchKey(0, VTermKey.TAB)
        }
        KeyBarButton("CTRL", foreground, ctrlOn) {
            modifierManager.toggleCtrl()
            ctrlOn = modifierManager.isCtrlActive()
        }
        KeyBarButton("ALT", foreground, altOn) {
            modifierManager.toggleAlt()
            altOn = modifierManager.isAltActive()
        }
        KeyBarButton("↑", foreground, false) {
            emulator.dispatchKey(0, VTermKey.UP)
        }
        KeyBarButton("↓", foreground, false) {
            emulator.dispatchKey(0, VTermKey.DOWN)
        }
        KeyBarButton("←", foreground, false) {
            emulator.dispatchKey(0, VTermKey.LEFT)
        }
        KeyBarButton("→", foreground, false) {
            emulator.dispatchKey(0, VTermKey.RIGHT)
        }
        KeyBarButton("HOME", foreground, false) {
            emulator.dispatchKey(0, VTermKey.HOME)
        }
        KeyBarButton("END", foreground, false) {
            emulator.dispatchKey(0, VTermKey.END)
        }
        KeyBarButton("PGUP", foreground, false) {
            emulator.dispatchKey(0, VTermKey.PAGEUP)
        }
        KeyBarButton("PGDN", foreground, false) {
            emulator.dispatchKey(0, VTermKey.PAGEDOWN)
        }
        KeyBarButton("/", foreground, false) {
            emulator.dispatchCharacter(0, '/')
        }
        KeyBarButton("|", foreground, false) {
            emulator.dispatchCharacter(0, '|')
        }
        KeyBarButton("-", foreground, false) {
            emulator.dispatchCharacter(0, '-')
        }
        KeyBarButton("~", foreground, false) {
            emulator.dispatchCharacter(0, '~')
        }
        // ── Utility buttons ──
        KeyBarButton("📄", foreground, false) { onPaste() }
        KeyBarButton("📋", foreground, false) { onRequestSnippets() }
        KeyBarButton("📑", foreground, false) { onCopyAll() }
        KeyBarButton("🗑", foreground, false) { onClear() }
        KeyBarButton("⌨", foreground, false) { onRequestShowKeyboard() }
    }
}

@Composable
private fun KeyBarButton(
    label: String,
    foreground: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (active) foreground.copy(alpha = 0.25f) else foreground.copy(alpha = 0.08f)
    val fg = if (active) foreground else foreground.copy(alpha = 0.85f)
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
