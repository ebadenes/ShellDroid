package io.shelldroid.feature.terminal

import android.app.Activity
import android.graphics.Typeface
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
    // Bridge is populated in attach() below. Observe it, then collect the
    // nested emulator flow whenever the bridge is non-null.
    val bridge by viewModel.bridge.collectAsStateWithLifecycle()
    val emulator by (
        bridge?.emulator
            ?: kotlinx.coroutines.flow.MutableStateFlow(null)
    ).collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }
    val modifierManager = remember { ShellDroidModifierManager() }

    val context = LocalContext.current
    val view = LocalView.current
    // WindowInsetsControllerCompat.show(ime()) is the modern, reliable
    // way to show the keyboard at the window level, not bound to a
    // specific target View like InputMethodManager.showSoftInput is.
    val insetsController: WindowInsetsControllerCompat? = remember(context, view) {
        val activity = context as? Activity ?: return@remember null
        WindowCompat.getInsetsController(activity.window, view)
    }

    // "Our intent" state. We do NOT auto-sync this from system IME
    // visibility — that caused a feedback loop where hiding the IME
    // locked us out of being able to bring it back.
    var showSoftKeyboard by remember { mutableStateOf(true) }

    // Dynamic font size so the volume keys can zoom in/out without
    // reopening the terminal. Initial value comes from the skin.
    var fontSizeSp by remember(skin.textSizeSp) { mutableStateOf(skin.textSizeSp) }

    // Install a hardware key interceptor while this screen is on screen.
    // Volume Up  -> zoom in (+1 sp, capped at 36)
    // Volume Down -> zoom out (-1 sp, floor 6)
    DisposableEffect(Unit) {
        HardwareKeyInterceptor.handler = { keyCode, _ ->
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    fontSizeSp = (fontSizeSp + 1f).coerceAtMost(36f)
                    true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    fontSizeSp = (fontSizeSp - 1f).coerceAtLeast(6f)
                    true
                }
                else -> false
            }
        }
        onDispose { HardwareKeyInterceptor.handler = null }
    }

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
                    viewModel.disconnectAndRelease()
                    onBack()
                }) { Text("Desconectar") }
            },
        )
    }

    fun forceShowKeyboard() {
        showSoftKeyboard = true
        try {
            focusRequester.requestFocus()
        } catch (_: Throwable) {
            // focus may not be attached yet; fine
        }
        insetsController?.show(WindowInsetsCompat.Type.ime())
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
                    initialFontSize = fontSizeSp.sp,
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
