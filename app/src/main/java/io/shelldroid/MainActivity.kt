package io.shelldroid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.shelldroid.core.security.BiometricGate
import io.shelldroid.core.security.LockManager
import io.shelldroid.core.ui.ShellDroidTheme
import io.shelldroid.feature.terminal.HardwareKeyInterceptor
import io.shelldroid.feature.terminal.TerminalLaunchRequest
import io.shelldroid.nav.ShellDroidNavHost
import io.shelldroid.ui.lock.LockScreen
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var lockManager: LockManager

    private var isLocked by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* granted or not — the FGS runs either way, notif just won't show */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        maybeDispatchTerminalLaunch(intent)
        requestNotificationPermissionIfNeeded()
        checkLockOnResume()
        setContent {
            ShellDroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ShellDroidNavHost()
                        if (isLocked) {
                            val biometricGate = BiometricGate(this@MainActivity)
                            LockScreen(
                                onUnlockClick = { triggerBiometric(biometricGate) },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkLockOnResume() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                isLocked = lockManager.isLocked()
                if (isLocked) {
                    // Try biometric immediately on resume if available
                    val gate = BiometricGate(this@MainActivity)
                    if (gate.isAvailable()) {
                        triggerBiometric(gate)
                    }
                }
            }
        }
    }

    private fun triggerBiometric(gate: BiometricGate) {
        gate.authenticate(
            onSuccess = {
                lifecycleScope.launch {
                    lockManager.markUnlocked()
                    isLocked = false
                }
            },
            onError = { _, _ -> },
            onFailed = { },
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeDispatchTerminalLaunch(intent)
    }

    /**
     * If the launching intent contains a terminal-host extra (e.g. it was
     * fired from the foreground service notification "Abrir terminal"
     * action), republish the host id on [TerminalLaunchRequest] so the
     * nav host picks it up and navigates to the terminal destination.
     */
    private fun maybeDispatchTerminalLaunch(intent: Intent?) {
        val hostId = intent?.getStringExtra(TerminalLaunchRequest.EXTRA_HOST_ID) ?: return
        TerminalLaunchRequest.request(hostId)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Route volume-up / volume-down key events to the currently-installed
     * hardware key interceptor (typically [io.shelldroid.feature.terminal.TerminalScreen]
     * when visible). Consuming both DOWN and UP prevents the system audio
     * feedback.
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
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
