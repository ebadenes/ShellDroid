package io.shelldroid.feature.terminal

import android.view.KeyEvent
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.view.TerminalView

/**
 * What each non-regular key does when tapped.
 * Arrows / home / end / pgup / pgdn all go through
 * [TerminalView.handleKeyCode] so the resulting escape sequence
 * respects cursor-keys / keypad application modes of the emulator.
 *
 * ESC and TAB write their literal code point directly.
 * CTRL and ALT toggle the sticky one-shot state on the view client;
 * the NEXT regular IME keystroke gets the modifier applied.
 */
sealed interface TerminalKeyAction {
    data object Esc : TerminalKeyAction
    data object Tab : TerminalKeyAction
    data object Ctrl : TerminalKeyAction
    data object Alt : TerminalKeyAction
    data object Up : TerminalKeyAction
    data object Down : TerminalKeyAction
    data object Left : TerminalKeyAction
    data object Right : TerminalKeyAction
    data object Home : TerminalKeyAction
    data object End : TerminalKeyAction
    data object PgUp : TerminalKeyAction
    data object PgDn : TerminalKeyAction
    data object Slash : TerminalKeyAction
    data object Pipe : TerminalKeyAction
    data object Dash : TerminalKeyAction
    data object Tilde : TerminalKeyAction
    data object Keyboard : TerminalKeyAction
}

private data class KeyDef(val label: String, val action: TerminalKeyAction)

private val DEFAULT_KEYS = listOf(
    KeyDef("ESC", TerminalKeyAction.Esc),
    KeyDef("TAB", TerminalKeyAction.Tab),
    KeyDef("CTRL", TerminalKeyAction.Ctrl),
    KeyDef("ALT", TerminalKeyAction.Alt),
    KeyDef("↑", TerminalKeyAction.Up),
    KeyDef("↓", TerminalKeyAction.Down),
    KeyDef("←", TerminalKeyAction.Left),
    KeyDef("→", TerminalKeyAction.Right),
    KeyDef("HOME", TerminalKeyAction.Home),
    KeyDef("END", TerminalKeyAction.End),
    KeyDef("PGUP", TerminalKeyAction.PgUp),
    KeyDef("PGDN", TerminalKeyAction.PgDn),
    KeyDef("/", TerminalKeyAction.Slash),
    KeyDef("|", TerminalKeyAction.Pipe),
    KeyDef("-", TerminalKeyAction.Dash),
    KeyDef("~", TerminalKeyAction.Tilde),
    KeyDef("⌨", TerminalKeyAction.Keyboard),
)

@Composable
fun TerminalKeyBar(
    background: Color,
    foreground: Color,
    ctrlActive: Boolean,
    altActive: Boolean,
    onAction: (TerminalKeyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        DEFAULT_KEYS.forEach { key ->
            val active = when (key.action) {
                TerminalKeyAction.Ctrl -> ctrlActive
                TerminalKeyAction.Alt -> altActive
                else -> false
            }
            KeyBarButton(
                label = key.label,
                active = active,
                foreground = foreground,
                onClick = { onAction(key.action) },
            )
        }
    }
}

@Composable
private fun KeyBarButton(
    label: String,
    active: Boolean,
    foreground: Color,
    onClick: () -> Unit,
) {
    val bg = if (active) foreground.copy(alpha = 0.25f) else foreground.copy(alpha = 0.08f)
    val fg = if (active) foreground else foreground.copy(alpha = 0.85f)
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .pointerInput(onClick) {
                awaitEachGesture(onClick)
            }
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

/**
 * Wait for the first down -> up sequence and fire [onTap]. This gives
 * us a plain tap handler that doesn't interfere with swipe gestures.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.awaitEachGesture(onTap: () -> Unit) {
    while (true) {
        awaitPointerEventScope {
            awaitPointerEvent() // DOWN
            // wait for UP
            do {
                val ev = awaitPointerEvent()
                if (ev.changes.all { !it.pressed }) {
                    onTap()
                    return@awaitPointerEventScope
                }
            } while (true)
        }
    }
}

/**
 * Execute a [TerminalKeyAction] against the attached [TerminalView].
 * Regular characters are sent through `writeCodePoint`, arrows / nav
 * keys through `handleKeyCode` so escape sequences respect the
 * emulator's current cursor/keypad modes.
 */
fun applyKeyAction(
    action: TerminalKeyAction,
    view: TerminalView,
    client: ShellDroidTerminalViewClient,
    showKeyboard: () -> Unit,
) {
    val session = view.mTermSession ?: return
    when (action) {
        TerminalKeyAction.Esc -> session.writeCodePoint(false, 27)
        TerminalKeyAction.Tab -> session.writeCodePoint(false, 9)
        TerminalKeyAction.Ctrl -> client.setCtrlSticky(!client.isCtrlSticky())
        TerminalKeyAction.Alt -> client.setAltSticky(!client.isAltSticky())
        TerminalKeyAction.Up -> view.handleKeyCode(KeyEvent.KEYCODE_DPAD_UP, 0)
        TerminalKeyAction.Down -> view.handleKeyCode(KeyEvent.KEYCODE_DPAD_DOWN, 0)
        TerminalKeyAction.Left -> view.handleKeyCode(KeyEvent.KEYCODE_DPAD_LEFT, 0)
        TerminalKeyAction.Right -> view.handleKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT, 0)
        TerminalKeyAction.Home -> view.handleKeyCode(KeyEvent.KEYCODE_MOVE_HOME, 0)
        TerminalKeyAction.End -> view.handleKeyCode(KeyEvent.KEYCODE_MOVE_END, 0)
        TerminalKeyAction.PgUp -> view.handleKeyCode(KeyEvent.KEYCODE_PAGE_UP, 0)
        TerminalKeyAction.PgDn -> view.handleKeyCode(KeyEvent.KEYCODE_PAGE_DOWN, 0)
        TerminalKeyAction.Slash -> session.writeCodePoint(false, '/'.code)
        TerminalKeyAction.Pipe -> session.writeCodePoint(false, '|'.code)
        TerminalKeyAction.Dash -> session.writeCodePoint(false, '-'.code)
        TerminalKeyAction.Tilde -> session.writeCodePoint(false, '~'.code)
        TerminalKeyAction.Keyboard -> showKeyboard()
    }
}
