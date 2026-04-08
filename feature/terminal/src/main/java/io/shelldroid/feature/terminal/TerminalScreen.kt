package io.shelldroid.feature.terminal

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import io.shelldroid.feature.terminal.skin.TerminalSkin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    hostId: String,
    onBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val skin by viewModel.skin.collectAsStateWithLifecycle()

    var showBackDialog by remember { mutableStateOf(false) }

    LaunchedEffect(hostId) { viewModel.loadTitle(hostId) }

    BackHandler { showBackDialog = true }

    if (showBackDialog) {
        AlertDialog(
            onDismissRequest = { showBackDialog = false },
            title = { Text("¿Mantener la conexión?") },
            text = {
                Text(
                    "La sesión SSH puede quedarse activa en segundo plano " +
                        "para reanudarla más rápido, o desconectarse completamente."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBackDialog = false
                    onBack()
                }) {
                    Text("Mantener")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBackDialog = false
                    viewModel.disconnectHost(hostId)
                    onBack()
                }) {
                    Text("Desconectar")
                }
            },
        )
    }

    Scaffold(
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
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            factory = { ctx ->
                val textSizePx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    skin.textSizeSp,
                    ctx.resources.displayMetrics,
                )
                val (cellW, cellH) = measureMonoCell(textSizePx)

                TerminalView(ctx, null).also { view ->
                    view.setTerminalViewClient(NoOpTerminalViewClient())
                    view.setTextSize(textSizePx.toInt())
                    view.setBackgroundColor(skin.background)

                    view.isFocusable = true
                    view.isFocusableInTouchMode = true
                    view.setOnClickListener { v ->
                        v.requestFocus()
                        showSoftKeyboard(ctx, v)
                    }

                    view.addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
                        val w = r - l
                        val h = b - t
                        if (w <= 0 || h <= 0) return@addOnLayoutChangeListener
                        val cols = (w / cellW).coerceAtLeast(1)
                        val rows = (h / cellH).coerceAtLeast(1)
                        if (viewModel.session.value == null) {
                            viewModel.start(hostId, cols, rows, cellW, cellH)
                        } else {
                            viewModel.resize(cols, rows, cellW, cellH)
                        }
                    }
                }
            },
            update = { view ->
                val s = session
                if (s != null && view.mTermSession !== s) {
                    view.attachSession(s)
                    view.setBackgroundColor(skin.background)
                    applySkinToEmulator(s, skin)
                    view.requestFocus()
                    showSoftKeyboard(view.context, view)
                }
                // Skin changes happen rarely; let the next attachSession
                // pick them up (or reopen the screen). We skip the per-
                // recomposition re-apply to avoid useless work on every
                // unrelated state change (title, etc).
            },
        )
    }
}

private fun applySkinToEmulator(session: SshTerminalSession, skin: TerminalSkin) {
    val em = session.emulator ?: return
    val colors = em.mColors.mCurrentColors
    // ANSI 0..15
    for (i in 0 until 16) colors[i] = skin.ansi[i]
    // Special indices
    colors[TextStyle.COLOR_INDEX_FOREGROUND] = skin.foreground
    colors[TextStyle.COLOR_INDEX_BACKGROUND] = skin.background
    colors[TextStyle.COLOR_INDEX_CURSOR] = skin.cursor
}

private fun showSoftKeyboard(ctx: Context, view: android.view.View) {
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
}

/**
 * Approximate Termux's TerminalRenderer cell dimensions with a Paint that
 * matches its monospace / text size setup. This avoids poking at
 * package-private renderer fields via reflection.
 */
private fun measureMonoCell(textSizePx: Float): Pair<Int, Int> {
    val paint = Paint().apply {
        typeface = Typeface.MONOSPACE
        this.textSize = textSizePx
        isAntiAlias = true
    }
    val cellW = paint.measureText("M").toInt().coerceAtLeast(1)
    val fm = paint.fontMetricsInt
    val cellH = (fm.descent - fm.ascent).coerceAtLeast(1)
    return cellW to cellH
}
