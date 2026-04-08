package io.shelldroid.feature.terminal

import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.connectbot.terminal.Terminal

/**
 * Terminal screen built on ConnectBot's termlib.
 *
 * IME integration follows ConnectBot's ConsoleScreen pattern exactly:
 *
 *  - Scaffold's contentWindowInsets unions the IME animation target so
 *    the content area reports the correct height during the IME open /
 *    close animation (not just the end state).
 *  - The content box `.consumeWindowInsets(innerPadding)
 *    .windowInsetsPadding(WindowInsets.imeAnimationTarget)` so the
 *    Terminal shrinks above the IME.
 *  - The actual system IME visibility is read from `WindowInsets.ime`
 *    and mirrored into our `showSoftKeyboard` state: when the user
 *    dismisses the IME externally (back button, swipe), our state
 *    follows. Tap on the Terminal sets our state to `true` and termlib
 *    re-shows the IME.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    // Our request state ("do we want the IME visible?"). Initially true
    // so the keyboard comes up right after the shell is attached.
    var showSoftKeyboard by remember { mutableStateOf(true) }

    // Actual system IME visibility, derived from WindowInsets.ime. Used to
    // detect external dismissal (back button, swipe down) so we can sync
    // our request state — otherwise termlib would think we still want the
    // keyboard visible and re-show it.
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val imeBottomDp = with(density) { imeInsets.getBottom(density).toDp() }
    val systemImeVisible = imeBottomDp > 0.dp
    var hasImeBeenVisible by remember { mutableStateOf(false) }
    LaunchedEffect(systemImeVisible) {
        if (systemImeVisible) hasImeBeenVisible = true
        if (hasImeBeenVisible && !systemImeVisible && showSoftKeyboard) {
            showSoftKeyboard = false
        }
    }

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
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .union(WindowInsets.imeAnimationTarget),
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
                    .consumeWindowInsets(inner)
                    .windowInsetsPadding(WindowInsets.imeAnimationTarget),
                typeface = Typeface.MONOSPACE,
                initialFontSize = skin.textSizeSp.sp,
                keyboardEnabled = true,
                showSoftKeyboard = showSoftKeyboard,
                focusRequester = focusRequester,
                forcedSize = null,
                modifierManager = modifierManager,
                onSelectionControllerAvailable = { /* no-op for now */ },
                onTerminalTap = {
                    // User tapped the terminal. If the IME is hidden, ask
                    // termlib to show it again; otherwise leave it alone.
                    if (!systemImeVisible) {
                        showSoftKeyboard = true
                        focusRequester.requestFocus()
                    }
                },
                onImeVisibilityChanged = { visible ->
                    // termlib's own callback; we let WindowInsets.ime be
                    // the source of truth via the LaunchedEffect above.
                    if (visible) showSoftKeyboard = true
                },
                onHyperlinkClick = { /* TODO: launch in browser */ },
            )
        }
    }
}
