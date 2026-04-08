package io.shelldroid.feature.terminal

import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.connectbot.terminal.Terminal

/**
 * Terminal screen built on ConnectBot's termlib. The heavy lifting
 * (rendering, IME integration, focus, selection, hyperlinks, gesture
 * handling) is all inside [Terminal]. We only provide:
 *
 *  - the [TerminalEmulator] instance (owned by [TerminalBridge] inside
 *    the [TerminalViewModel]),
 *  - a [ShellDroidModifierManager] for sticky Ctrl/Alt/Shift state,
 *  - a [FocusRequester] and a soft-keyboard visibility flag,
 *  - the top app bar and back-dialog plumbing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    hostId: String,
    onBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel(),
) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    val skin by viewModel.skin.collectAsStateWithLifecycle()
    val emulator by viewModel.bridge.emulator.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }
    val modifierManager = remember { ShellDroidModifierManager() }
    var showSoftKeyboard by remember { mutableStateOf(true) }
    var showBackDialog by remember { mutableStateOf(false) }

    LaunchedEffect(hostId, skin.background, skin.foreground) {
        viewModel.attach(
            hostId = hostId,
            cols = 80,
            rows = 24,
            foreground = skin.foreground,
            background = skin.background,
        )
    }

    BackHandler { showBackDialog = true }

    if (showBackDialog) {
        AlertDialog(
            onDismissRequest = { showBackDialog = false },
            title = { Text("¿Mantener la conexión?") },
            text = {
                Text(
                    "La sesión SSH puede quedarse activa en segundo plano " +
                        "para reanudarla más rápido, o desconectarse completamente.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBackDialog = false
                    onBack()
                }) { Text("Mantener") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBackDialog = false
                    viewModel.disconnectAndDetach()
                    onBack()
                }) { Text("Desconectar") }
            },
        )
    }

    Scaffold(
        containerColor = Color(skin.background),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { showBackDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { inner ->
        val em = emulator
        if (em != null) {
            Terminal(
                terminalEmulator = em,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .imePadding(),
                typeface = Typeface.MONOSPACE,
                initialFontSize = skin.textSizeSp.sp,
                keyboardEnabled = true,
                showSoftKeyboard = showSoftKeyboard,
                focusRequester = focusRequester,
                forcedSize = null,
                modifierManager = modifierManager,
                onSelectionControllerAvailable = { /* no-op for now */ },
                onTerminalTap = {
                    // Re-show the keyboard if the user dismissed it.
                    showSoftKeyboard = true
                    focusRequester.requestFocus()
                },
                onImeVisibilityChanged = { visible ->
                    showSoftKeyboard = visible
                },
                onHyperlinkClick = { /* TODO: launch in browser */ },
            )
        }
    }
}
