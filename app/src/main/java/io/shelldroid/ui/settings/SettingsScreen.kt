package io.shelldroid.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import io.shelldroid.core.ui.ThemeMode
import io.shelldroid.ui.settings.AppLanguage
import io.shelldroid.R

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
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state

    var showSkinPicker by remember { mutableStateOf(false) }
    var showThemeModePicker by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    if (showSkinPicker) {
        AlertDialog(
            onDismissRequest = { showSkinPicker = false },
            title = { Text("Tema del terminal") },
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
                TextButton(onClick = { showSkinPicker = false }) { Text("Cerrar") }
            },
        )
    }

    if (showThemeModePicker) {
        val modes = listOf(
            ThemeMode.SYSTEM to "Seguir al sistema",
            ThemeMode.DARK to "Oscuro",
            ThemeMode.LIGHT to "Claro",
        )
        AlertDialog(
            onDismissRequest = { showThemeModePicker = false },
            title = { Text("Modo de la app") },
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
                TextButton(onClick = { showThemeModePicker = false }) { Text("Cerrar") }
            },
        )
    }

    if (showLanguagePicker) {
        AlertDialog(
            onDismissRequest = { showLanguagePicker = false },
            title = { Text("Idioma") },
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
                TextButton(onClick = { showLanguagePicker = false }) { Text("Cerrar") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
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
            SectionHeader("General")

            ListItem(
                headlineContent = { Text("Modo oscuro / claro") },
                supportingContent = {
                    val label = when (state.themeMode) {
                        ThemeMode.SYSTEM -> "Seguir al sistema"
                        ThemeMode.DARK -> "Oscuro"
                        ThemeMode.LIGHT -> "Claro"
                    }
                    Text(label)
                },
                modifier = Modifier.clickable { showThemeModePicker = true },
            )

            ListItem(
                headlineContent = { Text("Idioma") },
                supportingContent = { Text(state.language.label) },
                modifier = Modifier.clickable { showLanguagePicker = true },
            )

            HorizontalDivider()

            // ── Terminal ──────────────────────────────────────────
            SectionHeader("Terminal")

            ListItem(
                headlineContent = { Text("Tema / colores") },
                supportingContent = {
                    val name = viewModel.availableSkins
                        .firstOrNull { it.id == state.selectedSkinId }?.name ?: "—"
                    Text(name)
                },
                modifier = Modifier.clickable { showSkinPicker = true },
            )

            ListItem(
                headlineContent = { Text("Tamaño de fuente") },
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
                headlineContent = { Text("Mantener pantalla encendida") },
                supportingContent = { Text("Evita que la pantalla se apague durante una sesión") },
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
            SectionHeader("Seguridad")

            ListItem(
                headlineContent = { Text("Bloqueo con PIN") },
                supportingContent = { Text("Requiere PIN o biometría al abrir la app") },
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
                headlineContent = { Text("Tiempo de auto-bloqueo") },
                supportingContent = { Text(state.autoLockLabel) },
                modifier = Modifier.clickable {
                    // TODO: show picker dialog for auto-lock timeout
                },
            )

            ListItem(
                headlineContent = { Text("Borrar known hosts") },
                supportingContent = { Text("Elimina todas las claves de servidor guardadas") },
                modifier = Modifier.clickable {
                    viewModel.clearKnownHosts()
                },
            )

            HorizontalDivider()

            // ── Acerca de ─────────────────────────────────────────
            SectionHeader("Acerca de")

            ListItem(
                headlineContent = { Text("ShellDroid") },
                supportingContent = { Text("Versión ${state.appVersion}") },
            )

            ListItem(
                headlineContent = { Text("Licencias") },
                supportingContent = { Text("GPLv3 · Componentes de código abierto") },
                modifier = Modifier.clickable {
                    // TODO: open licenses screen or webview
                },
            )
        }
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
