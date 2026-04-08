package io.shelldroid.feature.terminal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.termux.view.TerminalView

@Composable
fun TerminalScreen(
    hostId: String,
    viewModel: TerminalViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            TerminalView(ctx, null).also { it.setTerminalViewClient(NoOpTerminalViewClient()) }
        },
        update = { view ->
            val s = session
            if (s != null && view.mTermSession !== s) {
                view.attachSession(s)
            }
        },
    )

    LaunchedEffect(hostId) {
        // TODO: measure cell size and call viewModel.resize on layout changes.
        viewModel.start(hostId = hostId, cols = 80, rows = 24, cellW = 12, cellH = 24)
    }
}

