package io.shelldroid.feature.terminal

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.graphics.Typeface
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.shelldroid.core.ui.R as UiR
import org.connectbot.terminal.Terminal

/**
 * Fullscreen terminal — no TopAppBar, no Scaffold. The terminal fills
 * the entire screen. Navigation and actions are handled via:
 *
 *  - **Tap** (IME hidden): show soft keyboard
 *  - **Tap** (IME visible): toggle hacker keyboard bar
 *  - **Back** (IME visible): hide IME
 *  - **Back** (IME hidden): show keep/disconnect dialog
 *  - **Hacker keyboard bar**: ESC, TAB, CTRL, ALT, arrows, symbols,
 *    plus utility buttons: paste, snippets, copy-all, clear, keyboard
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
    val bridge by viewModel.bridge.collectAsStateWithLifecycle()
    val emulator by (bridge?.emulator
        ?: kotlinx.coroutines.flow.MutableStateFlow(null))
        .collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }
    val modifierManager = remember { ShellDroidModifierManager() }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val view = LocalView.current
    val insetsController: WindowInsetsControllerCompat? = remember(context, view) {
        val activity = context as? Activity ?: return@remember null
        WindowCompat.getInsetsController(activity.window, view)
    }

    var showSoftKeyboard by remember { mutableStateOf(true) }
    var showHackerBar by remember { mutableStateOf(true) }
    var showBackDialog by remember { mutableStateOf(false) }
    var showSnippetPicker by remember { mutableStateOf(false) }

    // Font size state for volume zoom
    var fontSizeSp by remember(skin.textSizeSp) { mutableStateOf(skin.textSizeSp) }
    var showZoomIndicator by remember { mutableStateOf(false) }
    LaunchedEffect(fontSizeSp) {
        showZoomIndicator = true
        kotlinx.coroutines.delay(1200)
        showZoomIndicator = false
    }

    // Observe system IME visibility
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val systemImeVisible = imeBottom > 0

    // Navigate back when remote shell exits
    val bridgeState by (bridge?.state
        ?: kotlinx.coroutines.flow.MutableStateFlow(TerminalBridge.State.Idle))
        .collectAsStateWithLifecycle()
    LaunchedEffect(bridgeState) {
        if (bridgeState is TerminalBridge.State.Closed) onBack()
    }

    // Skin changes → push to running emulator
    LaunchedEffect(skin) {
        bridge?.applyColors(skin.ansi, skin.foreground, skin.background)
    }

    // Attach on first composition
    LaunchedEffect(hostId, skin.background, skin.foreground) {
        viewModel.attach(hostId, 80, 24, skin.foreground, skin.background)
    }

    // Volume keys zoom
    DisposableEffect(Unit) {
        HardwareKeyInterceptor.handler = { keyCode, _ ->
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    fontSizeSp = (fontSizeSp + 1f).coerceAtMost(36f)
                    viewModel.persistFontSize(fontSizeSp)
                    true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    fontSizeSp = (fontSizeSp - 1f).coerceAtLeast(6f)
                    viewModel.persistFontSize(fontSizeSp)
                    true
                }
                else -> false
            }
        }
        onDispose { HardwareKeyInterceptor.handler = null }
    }

    // ── Back handler ────────────────────────────────────────────
    // Catches BOTH button-back AND gesture-back (swipe from edge).
    // IME visible → close IME + hide bar. IME hidden → show dialog.
    BackHandler(enabled = true) {
        if (systemImeVisible) {
            insetsController?.hide(WindowInsetsCompat.Type.ime())
            showSoftKeyboard = false
            showHackerBar = false
        } else {
            showBackDialog = true
        }
    }

    fun forceShowKeyboard() {
        showSoftKeyboard = true
        try { focusRequester.requestFocus() } catch (_: Throwable) {}
        insetsController?.show(WindowInsetsCompat.Type.ime())
    }

    fun forceHideKeyboard() {
        insetsController?.hide(WindowInsetsCompat.Type.ime())
        showSoftKeyboard = false
    }

    // ── Dialogs ─────────────────────────────────────────────────
    if (showBackDialog) {
        AlertDialog(
            onDismissRequest = { showBackDialog = false },
            title = { Text(stringResource(UiR.string.keep_title)) },
            text = { Text(stringResource(UiR.string.keep_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showBackDialog = false
                    onBack()
                }) { Text(stringResource(UiR.string.keep)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBackDialog = false
                    viewModel.disconnectAndRelease()
                    onBack()
                }) { Text(stringResource(UiR.string.disconnect)) }
            },
        )
    }

    val snippets by viewModel.snippets.collectAsStateWithLifecycle()
    if (showSnippetPicker) {
        AlertDialog(
            onDismissRequest = { showSnippetPicker = false },
            title = { Text(stringResource(UiR.string.snippets)) },
            text = {
                if (snippets.isEmpty()) {
                    Text(stringResource(UiR.string.no_snippets_terminal))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(snippets, key = { it.id }) { snippet ->
                            ListItem(
                                headlineContent = { Text(snippet.name) },
                                supportingContent = {
                                    Text(snippet.command, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                },
                                modifier = Modifier.clickable {
                                    viewModel.runSnippet(snippet)
                                    showSnippetPicker = false
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSnippetPicker = false }) {
                    Text(stringResource(UiR.string.close))
                }
            },
        )
    }

    // ── Terminal content (fullscreen, no Scaffold) ───────────────
    val em = emulator
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(skin.background))
            .windowInsetsPadding(
                ScaffoldDefaults.contentWindowInsets
                    .union(WindowInsets.imeAnimationTarget)
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (em != null) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Terminal(
                        terminalEmulator = em,
                        modifier = Modifier.fillMaxSize(),
                        typeface = Typeface.MONOSPACE,
                        initialFontSize = fontSizeSp.sp,
                        keyboardEnabled = true,
                        showSoftKeyboard = showSoftKeyboard,
                        focusRequester = focusRequester,
                        forcedSize = null,
                        modifierManager = modifierManager,
                        onSelectionControllerAvailable = { /* termlib handles selection */ },
                        onTerminalTap = {
                            if (systemImeVisible) {
                                // IME open → toggle hacker bar
                                showHackerBar = !showHackerBar
                            } else {
                                // IME closed → show keyboard + bar
                                showHackerBar = true
                                forceShowKeyboard()
                            }
                        },
                        onImeVisibilityChanged = { visible ->
                            if (visible) {
                                showSoftKeyboard = true
                                showHackerBar = true
                            }
                        },
                        onHyperlinkClick = { url ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (_: Throwable) {}
                        },
                    )

                    // Zoom indicator
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showZoomIndicator,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp),
                    ) {
                        Text(
                            text = "${fontSizeSp.toInt()} sp",
                            color = Color.White,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }

                // Hacker keyboard bar with utility buttons
                AnimatedVisibility(visible = showHackerBar) {
                    TerminalKeyBar(
                        emulator = em,
                        modifierManager = modifierManager,
                        background = Color(skin.background),
                        foreground = Color(skin.foreground),
                        onRequestShowKeyboard = { forceShowKeyboard() },
                        onRequestSnippets = { showSnippetPicker = true },
                        onPaste = {
                            val text = clipboardManager.getText()?.text ?: return@TerminalKeyBar
                            bridge?.sendInput(text.toByteArray(Charsets.UTF_8))
                        },
                        onCopyAll = {
                            try {
                                val output = em.getLastCommandOutput()
                                if (!output.isNullOrEmpty()) {
                                    clipboardManager.setText(AnnotatedString(output))
                                }
                            } catch (_: Throwable) {}
                        },
                        onClear = { em.clearScreen() },
                    )
                }
            }
        }
    }
}
