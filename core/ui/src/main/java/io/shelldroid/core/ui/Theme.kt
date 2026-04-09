package io.shelldroid.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** App-wide theme mode selection. */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

object AppTheme {
    var mode: ThemeMode by mutableStateOf(ThemeMode.SYSTEM)
}

// ── Abyss palette (from shelldroid-colors.xml) ──────────────────
private val AbyssDark = darkColorScheme(
    primary = Color(0xFF00C2FF),
    onPrimary = Color(0xFF0A0E1A),
    primaryContainer = Color(0xFF0A3A52),
    onPrimaryContainer = Color(0xFFE2EAF4),
    secondary = Color(0xFF6B8099),
    onSecondary = Color(0xFF0A0E1A),
    secondaryContainer = Color(0xFF1E2D45),
    onSecondaryContainer = Color(0xFFA0B4C8),
    tertiary = Color(0xFF00E5A0),
    onTertiary = Color(0xFF003D2A),
    background = Color(0xFF0A0E1A),
    onBackground = Color(0xFFE2EAF4),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFE2EAF4),
    surfaceVariant = Color(0xFF151E2E),
    onSurfaceVariant = Color(0xFFA0B4C8),
    outline = Color(0xFF1E2D45),
    outlineVariant = Color(0xFF141F30),
    error = Color(0xFFFF4D6A),
    onError = Color(0xFF3D0010),
)

private val AbyssLight = lightColorScheme(
    primary = Color(0xFF006A8E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBDE9FF),
    onPrimaryContainer = Color(0xFF001F2B),
    secondary = Color(0xFF4E6170),
    onSecondary = Color.White,
    background = Color(0xFFF8FAFF),
    onBackground = Color(0xFF0A0E1A),
    surface = Color(0xFFF8FAFF),
    onSurface = Color(0xFF0A0E1A),
    surfaceVariant = Color(0xFFDCE4EB),
    onSurfaceVariant = Color(0xFF3F4B55),
    outline = Color(0xFF6F7E8A),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

@Composable
fun ShellDroidTheme(content: @Composable () -> Unit) {
    val isDark = when (AppTheme.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (isDark) AbyssDark else AbyssLight,
        content = content,
    )
}
