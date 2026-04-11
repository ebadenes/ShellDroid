package io.shelldroid.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import io.shelldroid.core.security.AutoLockMode
import io.shelldroid.core.ui.ThemeMode
import io.shelldroid.ui.settings.AppLanguage
import io.shelldroid.core.ui.R as UiR

/**
 * Settings screen modelled after JuiceSSH's preference layout.
 *
 * Categories:
 *  1. Terminal — skin/theme, font size, keep screen on
 *  2. Security — PIN lock toggle, auto-lock timeout, clear known hosts
 *  3. About — app version, licenses
 *
 * Persistence is mixed: skin selection lives in [TerminalSkinRepository]
 * (in-memory for now, DataStore later); font size override is stored in
 * the ViewModel; security prefs go through [LockManager] when wired.
 * Each setting reads its current value from the VM on composition and
 * writes back on change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenKnownHosts: () -> Unit = {},
    onOpenLicenses: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state
    val context = LocalContext.current

    var showSkinPicker by remember { mutableStateOf(false) }
    var showThemeModePicker by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showAutoLockPicker by remember { mutableStateOf(false) }

    if (showSkinPicker) {
        AlertDialog(
            onDismissRequest = { showSkinPicker = false },
            title = { Text(stringResource(UiR.string.terminal_theme)) },
            text = {
                Column {
                    viewModel.availableSkins.forEach { skin ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectSkin(skin.id)
                                    showSkinPicker = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = skin.id == state.selectedSkinId,
                                onClick = {
                                    viewModel.selectSkin(skin.id)
                                    showSkinPicker = false
                                },
                            )
                            Text(skin.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSkinPicker = false }) { Text(stringResource(UiR.string.close)) }
            },
        )
    }

    if (showThemeModePicker) {
        val modes = listOf(
            ThemeMode.SYSTEM to stringResource(UiR.string.theme_system),
            ThemeMode.DARK to stringResource(UiR.string.theme_dark),
            ThemeMode.LIGHT to stringResource(UiR.string.theme_light),
        )
        AlertDialog(
            onDismissRequest = { showThemeModePicker = false },
            title = { Text(stringResource(UiR.string.app_mode)) },
            text = {
                Column {
                    modes.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setThemeMode(mode)
                                    showThemeModePicker = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.themeMode == mode,
                                onClick = {
                                    viewModel.setThemeMode(mode)
                                    showThemeModePicker = false
                                },
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeModePicker = false }) { Text(stringResource(UiR.string.close)) }
            },
        )
    }

    if (showLanguagePicker) {
        AlertDialog(
            onDismissRequest = { showLanguagePicker = false },
            title = { Text(stringResource(UiR.string.settings_language)) },
            text = {
                Column {
                    AppLanguage.entries.forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setLanguage(lang)
                                    showLanguagePicker = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.language == lang,
                                onClick = {
                                    viewModel.setLanguage(lang)
                                    showLanguagePicker = false
                                },
                            )
                            Text(lang.label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguagePicker = false }) { Text(stringResource(UiR.string.close)) }
            },
        )
    }

    if (showAutoLockPicker) {
        val modes = listOf(
            AutoLockMode.SYSTEM_SCREEN_OFF,
            AutoLockMode.IMMEDIATE,
            AutoLockMode.ONE_MIN,
            AutoLockMode.FIVE_MIN,
            AutoLockMode.FIFTEEN_MIN,
            AutoLockMode.NEVER,
        )
        AlertDialog(
            onDismissRequest = { showAutoLockPicker = false },
            title = { Text(stringResource(UiR.string.settings_autolock)) },
            text = {
                Column {
                    modes.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setAutoLockMode(mode)
                                    showAutoLockPicker = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.autoLockMode == mode,
                                onClick = {
                                    viewModel.setAutoLockMode(mode)
                                    showAutoLockPicker = false
                                },
                            )
                            Text(autoLockLabel(mode), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutoLockPicker = false }) { Text(stringResource(UiR.string.close)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(UiR.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── General ───────────────────────────────────────────
            SectionHeader(stringResource(UiR.string.settings_general))

            ListItem(
                headlineContent = { Text(stringResource(UiR.string.settings_dark_light)) },
                supportingContent = {
                    val label = when (state.themeMode) {
                        ThemeMode.SYSTEM -> stringResource(UiR.string.theme_system)
                        ThemeMode.DARK -> stringResource(UiR.string.theme_dark)
                        ThemeMode.LIGHT -> stringResource(UiR.string.theme_light)
                    }
                    Text(label)
                },
                modifier = Modifier.clickable { showThemeModePicker = true },
            )

            ListItem(
                headlineContent = { Text(stringResource(UiR.string.settings_language)) },
                supportingContent = { Text(state.language.label) },
                modifier = Modifier.clickable { showLanguagePicker = true },
            )

            HorizontalDivider()

            // ── Terminal ──────────────────────────────────────────
            SectionHeader(stringResource(UiR.string.settings_terminal))

            ListItem(
                headlineContent = { Text(stringResource(UiR.string.settings_theme_colors)) },
                supportingContent = {
                    val name = viewModel.availableSkins
                        .firstOrNull { it.id == state.selectedSkinId }?.name ?: "—"
                    Text(name)
                },
                modifier = Modifier.clickable { showSkinPicker = true },
            )

            ListItem(
                headlineContent = { Text(stringResource(UiR.string.settings_font_size)) },
                supportingContent = { Text("${state.fontSizeSp.toInt()} sp") },
            )
            Slider(
                value = state.fontSizeSp,
                onValueChange = { viewModel.setFontSize(it) },
                valueRange = 6f..36f,
                steps = 29,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            ListItem(
                headlineContent = { Text(stringResource(UiR.string.settings_keep_screen_on)) },
                supportingContent = { Text(stringResource(UiR.string.settings_keep_screen_on_desc)) },
                trailingContent = {
                    Switch(
                        checked = state.keepScreenOn,
                        onCheckedChange = { viewModel.setKeepScreenOn(it) },
                    )
                },
                modifier = Modifier.clickable {
                    viewModel.setKeepScreenOn(!state.keepScreenOn)
                },
            )

            HorizontalDivider()

            // ── Seguridad ─────────────────────────────────────────
            SectionHeader(stringResource(UiR.string.settings_security))

            ListItem(
                headlineContent = { Text(stringResource(UiR.string.settings_pin_lock)) },
                supportingContent = { Text(stringResource(UiR.string.settings_pin_lock_desc)) },
                trailingContent = {
                    Switch(
                        checked = state.pinLockEnabled,
                        onCheckedChange = { viewModel.setPinLock(it) },
                    )
                },
                modifier = Modifier.clickable {
                    viewModel.setPinLock(!state.pinLockEnabled)
                },
            )

            ListItem(
                headlineContent = { Text(stringResource(UiR.string.settings_autolock)) },
                supportingContent = { Text(autoLockLabel(state.autoLockMode)) },
                modifier = Modifier.clickable { showAutoLockPicker = true },
            )

            ListItem(
                headlineContent = { Text(stringResource(UiR.string.manage_known_hosts)) },
                supportingContent = { Text(stringResource(UiR.string.manage_known_hosts_desc)) },
                modifier = Modifier.clickable { onOpenKnownHosts() },
            )

            HorizontalDivider()

            // ── Acerca de ─────────────────────────────────────────
            SectionHeader(stringResource(UiR.string.settings_about))

            ListItem(
                headlineContent = { Text(stringResource(UiR.string.about_rate)) },
                supportingContent = { Text(stringResource(UiR.string.about_rate_desc)) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                modifier = Modifier.clickable { openPlayStore(context) },
            )

            ListItem(
                headlineContent = { Text(stringResource(UiR.string.settings_licenses)) },
                supportingContent = { Text(stringResource(UiR.string.settings_licenses_desc)) },
                modifier = Modifier.clickable { onOpenLicenses() },
            )

            ListItem(
                headlineContent = { Text(stringResource(UiR.string.app_name)) },
                supportingContent = { Text(stringResource(UiR.string.version_format, state.appVersion)) },
            )

            // Web + GitHub split 50/50
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { openUrl(context, WEBSITE_URL) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Public,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(UiR.string.about_website))
                }
                OutlinedButton(
                    onClick = { openUrl(context, GITHUB_URL) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(UiR.string.about_github))
                }
            }
        }
    }
}

private const val WEBSITE_URL = "https://shelldroid.ebadenes.com/"
private const val GITHUB_URL = "https://github.com/ebadenes/ShellDroid"
private const val PLAY_STORE_PKG = "io.shelldroid"

/**
 * Opens the Play Store listing. Prefers the `market://` intent so the
 * Play Store app opens directly if installed; otherwise falls back to
 * the `https://play.google.com/...` web URL.
 */
private fun openPlayStore(context: android.content.Context) {
    val marketIntent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("market://details?id=$PLAY_STORE_PKG"),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(marketIntent)
    } catch (_: android.content.ActivityNotFoundException) {
        openUrl(context, "https://play.google.com/store/apps/details?id=$PLAY_STORE_PKG")
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    val intent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse(url),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: android.content.ActivityNotFoundException) {
        // No browser / Play Store installed — silently ignore.
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun autoLockLabel(mode: AutoLockMode): String = when (mode) {
    AutoLockMode.SYSTEM_SCREEN_OFF -> stringResource(UiR.string.settings_autolock_system)
    AutoLockMode.IMMEDIATE -> stringResource(UiR.string.settings_autolock_immediate)
    AutoLockMode.ONE_MIN -> stringResource(UiR.string.settings_autolock_1min)
    AutoLockMode.FIVE_MIN -> stringResource(UiR.string.settings_autolock_5min)
    AutoLockMode.FIFTEEN_MIN -> stringResource(UiR.string.settings_autolock_15min)
    AutoLockMode.NEVER -> stringResource(UiR.string.settings_autolock_never)
}
