package io.shelldroid.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** App-wide theme mode selection. */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

/**
 * Global observable holder for the current theme mode. Settings screen
 * writes via [AppTheme.mode], and [ShellDroidTheme] reads it in its
 * composable body. Because it's backed by a Compose [mutableStateOf],
 * any change triggers recomposition of the entire theme subtree.
 *
 * Will migrate to DataStore when we persist preferences.
 */
object AppTheme {
    var mode: ThemeMode by mutableStateOf(ThemeMode.SYSTEM)
}

@Composable
fun ShellDroidTheme(content: @Composable () -> Unit) {
    val isDark = when (AppTheme.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (isDark) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
