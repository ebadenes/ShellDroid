package io.shelldroid

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import io.shelldroid.core.ui.ShellDroidTheme
import io.shelldroid.feature.terminal.HardwareKeyInterceptor
import io.shelldroid.nav.ShellDroidNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ShellDroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ShellDroidNavHost()
                }
            }
        }
    }

    /**
     * Route volume-up / volume-down key events to the currently-installed
     * hardware key interceptor (typically [TerminalScreen] when visible).
     * Consuming both DOWN and UP prevents the system audio feedback.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val isVolume = keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (isVolume) {
            val handler = HardwareKeyInterceptor.handler
            if (handler != null) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (handler(keyCode, event)) return true
                } else if (event.action == KeyEvent.ACTION_UP) {
                    // Silence system-level handling of the UP half.
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
