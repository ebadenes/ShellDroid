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
        // ── Row 1: ESC / | - HOME UP END PGUP FN ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollRow1),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KeyBarButton("ESC", foreground, false) {
                haptic(); emulator.dispatchKey(0, VTermKey.ESCAPE)
            }
            KeyBarButton("/", foreground, false) {
                haptic(); emulator.dispatchCharacter(0, '/')
            }
            KeyBarButton("|", foreground, false) {
                haptic(); emulator.dispatchCharacter(0, '|')
            }
            KeyBarButton("-", foreground, false) {
                haptic(); emulator.dispatchCharacter(0, '-')
            }
            KeyBarButton("HOME", foreground, false) {
                haptic(); emulator.dispatchKey(0, VTermKey.HOME)
            }
            KeyBarButton("\u2191", foreground, false) {
                haptic(); emulator.dispatchKey(0, VTermKey.UP)
            }
            KeyBarButton("END", foreground, false) {
                haptic(); emulator.dispatchKey(0, VTermKey.END)
            }
            KeyBarButton("PGUP", foreground, false) {
                haptic(); emulator.dispatchKey(0, VTermKey.PAGEUP)
            }
            KeyBarButton("FN", foreground, fnMode) {
                haptic(); fnMode = !fnMode
            }
        }

        // ── Row 2 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollRow2),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (fnMode) {
                // F-keys row
                KeyBarButton("F1", foreground, false) {
                    haptic(); emulator.dispatchKey(0, VTermKey.FUNCTION_1)
                }
                KeyBarButton("F2", foreground, false) {
                    haptic(); emulator.dispatchKey(0, VTermKey.FUNCTION_2)
                }
                KeyBarButton("F3", foreground, false) {
                    haptic(); emulator.dispatchKey(0, VTermKey.FUNCTION_3)
                }
                KeyBarButton("F4", foreground, false) {
                    haptic(); emulator.dispatchKey(0, VTermKey.FUNCTION_4)
                }
                KeyBarButton("F5", foreground, false) {
                    haptic(); emulator.dispatchKey(0, VTermKey.FUNCTION_5)
                }
                KeyBarButton("F6", foreground, false) {
                    haptic(); emulator.dispatchKey(0, VTermKey.FUNCTION_6)
                }
                KeyBarButton("F7", foreground, false) {
                    haptic(); emulator.dispatchKey(0, VTermKey.FUNCTION_7)
                }
                KeyBarButton("F8", foreground, false) {
                    haptic(); emulator.dispatchKey(0, VTermKey.FUNCTION_8)
                }
                KeyBarButton("F9", foreground, false) {
                    haptic(); emulator.dispatchKey(0, VTermKey.FUNCTION_9)
                }
                KeyBarButton("F10", foreground, false) {
                    haptic(); emulator.dispatchKey(0, VTermKey.FUNCTION_10)
                }
                KeyBarButton("F11", foreground, false) {
                    haptic(); emulator.dispatchKey(0, VTermKey.FUNCTION_11)
                }
                KeyBarButton("F12", foreground, false) {
                    haptic(); emulator.dispatchKey(0, VTermKey.FUNCTION_12)
                }
            } else {
                // Navigation row
                KeyBarButton("TAB", foreground, false) {
                    haptic(); emulator.dispatchKey(0, VTermKey.TAB)
                }
                KeyBarButton("CTRL", foreground, ctrlOn) {
                    haptic()
                    modifierManager.toggleCtrl()
                    ctrlOn = modifierManager.isCtrlActive()
                }
                KeyBarButton("ALT", foreground, altOn) {
                    haptic()
                    modifierManager.toggleAlt()
                    altOn = modifierManager.isAltActive()
                }
                KeyBarButton("\u2190", foreground, false) {
                    haptic(); emulator.dispatchKey(0, VTermKey.LEFT)
                }
                KeyBarButton("\u2193", foreground, false) {
                    haptic(); emulator.dispatchKey(0, VTermKey.DOWN)
                }
                KeyBarButton("\u2192", foreground, false) {
                    haptic(); emulator.dispatchKey(0, VTermKey.RIGHT)
                }
                KeyBarButton("PGDN", foreground, false) {
                    haptic(); emulator.dispatchKey(0, VTermKey.PAGEDOWN)
                }
                KeyBarButton("\uD83D\uDCCB", foreground, false) {
                    haptic(); onRequestSnippets()
                }
                KeyBarButton("\u2328", foreground, false) {
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
