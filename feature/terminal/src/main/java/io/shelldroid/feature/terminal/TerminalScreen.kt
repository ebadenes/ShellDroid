package io.shelldroid.feature.terminal

import android.content.Context
import android.graphics.Typeface
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.connectbot.terminal.Terminal

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

    val context = LocalContext.current
    val view = LocalView.current
    val imm = remember(context) {
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    // "Our intent" state. We do NOT auto-sync this from system IME
    // visibility — that caused a feedback loop where hiding the IME
    // locked us out of being able to bring it back. Instead we drive
    // the InputMethodManager directly on tap / keybar ⌨.
    var showSoftKeyboard by remember { mutableStateOf(true) }

    // Observe system IME visibility only to know the CURRENT state, not
    // to override our intent. Used for the onTerminalTap logic.
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val imeBottom = imeInsets.getBottom(density)
    val systemImeVisible = imeBottom > 0

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

    fun forceShowKeyboard() {
        showSoftKeyboard = true
        focusRequester.requestFocus()
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner)
                .windowInsetsPadding(WindowInsets.imeAnimationTarget),
        ) {
            if (em != null) {
                Terminal(
                    terminalEmulator = em,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    typeface = Typeface.MONOSPACE,
                    initialFontSize = skin.textSizeSp.sp,
                    keyboardEnabled = true,
                    showSoftKeyboard = showSoftKeyboard,
                    focusRequester = focusRequester,
                    forcedSize = null,
                    modifierManager = modifierManager,
                    onSelectionControllerAvailable = { /* no-op */ },
                    onTerminalTap = {
                        // Always try to show the keyboard on tap. Safe
                        // idempotent call to IMM.
                        forceShowKeyboard()
                    },
                    onImeVisibilityChanged = { visible ->
                        if (visible) showSoftKeyboard = true
                        // Do not mirror visible=false back — that was the
                        // feedback loop that prevented recovery.
                    },
                    onHyperlinkClick = { /* TODO */ },
                )

                TerminalKeyBar(
                    emulator = em,
                    modifierManager = modifierManager,
                    background = Color(skin.background),
                    foreground = Color(skin.foreground),
                    onRequestShowKeyboard = { forceShowKeyboard() },
                )
            }
        }
    }
}
