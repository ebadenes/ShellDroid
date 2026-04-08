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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.termux.view.TerminalView

private const val TERMINAL_TEXT_SIZE_SP = 14f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    hostId: String,
    onBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()

    LaunchedEffect(hostId) { viewModel.loadTitle(hostId) }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                    TERMINAL_TEXT_SIZE_SP,
                    ctx.resources.displayMetrics,
                )
                val (cellW, cellH) = measureMonoCell(textSizePx)

                TerminalView(ctx, null).also { view ->
                    view.setTerminalViewClient(NoOpTerminalViewClient())
                    // TerminalView allocates its TerminalRenderer inside
                    // setTextSize(). Without this, attachSession() NPEs on
                    // mRenderer.mFontWidth inside updateSize().
                    view.setTextSize(textSizePx.toInt())

                    // Focus so the IME actually targets us.
                    view.isFocusable = true
                    view.isFocusableInTouchMode = true

                    // Tapping the terminal should bring up the soft keyboard.
                    // TerminalView's default onSingleTapUp does not do this on
                    // all devices, so we trigger it explicitly.
                    view.setOnClickListener { v ->
                        v.requestFocus()
                        showSoftKeyboard(ctx, v)
                    }

                    // Drive start/resize from the real laid-out size. First
                    // layout with a positive extent calls start(); subsequent
                    // layouts forward to resize().
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
                    view.requestFocus()
                    showSoftKeyboard(view.context, view)
                }
            },
        )
    }
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
