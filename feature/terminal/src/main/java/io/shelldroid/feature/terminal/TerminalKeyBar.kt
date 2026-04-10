package io.shelldroid.feature.terminal

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.VTermKey

/**
 * Two-row hacker keyboard bar (JuiceSSH style).
 *
 * Row 1: ESC  /  |  -  HOME  UP  END  PGUP  FN
 * Row 2 (nav): TAB  CTRL  ALT  LEFT  DOWN  RIGHT  PGDN  snippets  keyboard
 * Row 2 (FN):  F1 .. F12
 *
 * FN toggles Row 2 between navigation and function-key modes.
 * Each tap triggers haptic feedback (KEYBOARD_TAP).
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
    val view = LocalView.current
    val haptic: () -> Unit = {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    var ctrlOn by remember { mutableStateOf(false) }
    var altOn by remember { mutableStateOf(false) }
    var fnMode by remember { mutableStateOf(false) }

    val scrollRow1 = rememberScrollState()
    val scrollRow2 = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 3.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // ── Row 1: ESC / | - HOME UP END PGUP FN + paste, clear ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val w = Modifier.weight(1f)
            KeyBarButton("ESC", foreground, false, w) {
                haptic(); emulator.dispatchKey(0, VTermKey.ESCAPE)
            }
            KeyBarButton("/", foreground, false, w) {
                haptic(); emulator.dispatchCharacter(0, '/')
            }
            KeyBarButton("|", foreground, false, w) {
                haptic(); emulator.dispatchCharacter(0, '|')
            }
            KeyBarButton("-", foreground, false, w) {
                haptic(); emulator.dispatchCharacter(0, '-')
            }
            KeyBarButton("HOME", foreground, false, w) {
                haptic(); emulator.dispatchKey(0, VTermKey.HOME)
            }
            KeyBarButton("\u2191", foreground, false, w) {
                haptic(); emulator.dispatchKey(0, VTermKey.UP)
            }
            KeyBarButton("END", foreground, false, w) {
                haptic(); emulator.dispatchKey(0, VTermKey.END)
            }
            KeyBarButton("PGUP", foreground, false, w) {
                haptic(); emulator.dispatchKey(0, VTermKey.PAGEUP)
            }
            KeyBarButton("FN", foreground, fnMode, w) {
                haptic(); fnMode = !fnMode
            }
            KeyBarButton("📄", foreground, false, w) { haptic(); onPaste() }
            KeyBarButton("🗑", foreground, false, w) { haptic(); onClear() }
        }

        // ── Row 2 ──
        Row(
            modifier = Modifier.fillMaxWidth().let {
                if (fnMode) it.horizontalScroll(scrollRow2) else it
            },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (fnMode) {
                // F-keys row (scrollable — 12 buttons don't fit)
                for (i in 1..12) {
                    val key = when (i) {
                        1 -> VTermKey.FUNCTION_1; 2 -> VTermKey.FUNCTION_2
                        3 -> VTermKey.FUNCTION_3; 4 -> VTermKey.FUNCTION_4
                        5 -> VTermKey.FUNCTION_5; 6 -> VTermKey.FUNCTION_6
                        7 -> VTermKey.FUNCTION_7; 8 -> VTermKey.FUNCTION_8
                        9 -> VTermKey.FUNCTION_9; 10 -> VTermKey.FUNCTION_10
                        11 -> VTermKey.FUNCTION_11; else -> VTermKey.FUNCTION_12
                    }
                    KeyBarButton("F$i", foreground, false) {
                        haptic(); emulator.dispatchKey(0, key)
                    }
                }
            } else {
                // Navigation row (fills width)
                val w = Modifier.weight(1f)
                KeyBarButton("TAB", foreground, false, w) {
                    haptic(); emulator.dispatchKey(0, VTermKey.TAB)
                }
                KeyBarButton("CTRL", foreground, ctrlOn, w) {
                    haptic()
                    modifierManager.toggleCtrl()
                    ctrlOn = modifierManager.isCtrlActive()
                }
                KeyBarButton("ALT", foreground, altOn, w) {
                    haptic()
                    modifierManager.toggleAlt()
                    altOn = modifierManager.isAltActive()
                }
                KeyBarButton("\u2190", foreground, false, w) {
                    haptic(); emulator.dispatchKey(0, VTermKey.LEFT)
                }
                KeyBarButton("\u2193", foreground, false, w) {
                    haptic(); emulator.dispatchKey(0, VTermKey.DOWN)
                }
                KeyBarButton("\u2192", foreground, false, w) {
                    haptic(); emulator.dispatchKey(0, VTermKey.RIGHT)
                }
                KeyBarButton("PGDN", foreground, false, w) {
                    haptic(); emulator.dispatchKey(0, VTermKey.PAGEDOWN)
                }
                KeyBarButton("📋", foreground, false, w) {
                    haptic(); onRequestSnippets()
                }
                KeyBarButton("⌨", foreground, false, w) {
                    haptic(); onRequestShowKeyboard()
                }
            }
        }
    }
}

@Composable
private fun KeyBarButton(
    label: String,
    foreground: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (active) foreground.copy(alpha = 0.25f) else foreground.copy(alpha = 0.08f)
    val fg = if (active) foreground else foreground.copy(alpha = 0.85f)
    Box(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .clickable(onClick = onClick),
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
