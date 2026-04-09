package io.shelldroid.feature.terminal

import android.app.Activity
import android.graphics.Typeface
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.shelldroid.core.ui.R as UiR
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
    var showSnippetPicker by remember { mutableStateOf(false) }
    val snippets by viewModel.snippets.collectAsStateWithLifecycle()

    // Dynamic font size so the volume keys can zoom in/out without
    // reopening the terminal. Initial value comes from the skin.
    var fontSizeSp by remember(skin.textSizeSp) { mutableStateOf(skin.textSizeSp) }

    // Brief overlay showing the font size after a volume-key zoom.
    var showZoomIndicator by remember { mutableStateOf(false) }
    LaunchedEffect(fontSizeSp) {
        showZoomIndicator = true
        kotlinx.coroutines.delay(1200)
        showZoomIndicator = false
    }

    // Install a hardware key interceptor while this screen is on screen.
    // Volume Up  -> zoom in (+1 sp, capped at 36)
    // Volume Down -> zoom out (-1 sp, floor 6)
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

    // Observe system IME visibility only to know the CURRENT state, not
    // to override our intent. Used for the onTerminalTap logic.
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val imeBottom = imeInsets.getBottom(density)
    val systemImeVisible = imeBottom > 0

    var showBackDialog by remember { mutableStateOf(false) }

    // Navigate back when the remote shell exits (EOF, disconnect, error).
    val bridgeState by (bridge?.state
        ?: kotlinx.coroutines.flow.MutableStateFlow(TerminalBridge.State.Idle))
        .collectAsStateWithLifecycle()
    LaunchedEffect(bridgeState) {
        if (bridgeState is TerminalBridge.State.Closed) {
            onBack()
        }
    }

    // When the skin changes (user picked a different theme in Settings),
    // push the new colors into the running emulator without reopening.
    LaunchedEffect(skin) {
        bridge?.applyColors(skin.ansi, skin.foreground, skin.background)
    }

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

    fun forceShowKeyboard() {
        showSoftKeyboard = true
        try {
            focusRequester.requestFocus()
        } catch (_: Throwable) {
            // focus may not be attached yet; fine
        }
        insetsController?.show(WindowInsetsCompat.Type.ime())
    }

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
                            androidx.compose.material3.ListItem(
                                headlineContent = { Text(snippet.name) },
                                supportingContent = {
                                    Text(
                                        snippet.command,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
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
                        onSelectionControllerAvailable = { /* no-op */ },
                        onTerminalTap = { forceShowKeyboard() },
                        onImeVisibilityChanged = { visible ->
                            if (visible) showSoftKeyboard = true
                        },
                        onHyperlinkClick = { /* TODO */ },
                    )

                    // Zoom indicator overlay (top-center, fades after 1.2s)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showZoomIndicator,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.TopCenter)
                            .padding(top = 16.dp),
                    ) {
                        Text(
                            text = "${fontSizeSp.toInt()} sp",
                            color = Color.White,
                            modifier = Modifier
                                .background(
                                    Color.Black.copy(alpha = 0.7f),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }

                TerminalKeyBar(
                    emulator = em,
                    modifierManager = modifierManager,
                    background = Color(skin.background),
                    foreground = Color(skin.foreground),
                    onRequestShowKeyboard = { forceShowKeyboard() },
                    onRequestSnippets = { showSnippetPicker = true },
                )
            }
        }
    }
}
