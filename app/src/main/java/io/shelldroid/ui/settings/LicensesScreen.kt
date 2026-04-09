package io.shelldroid.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.shelldroid.core.ui.R as UiR

private data class LibraryInfo(
    val name: String,
    val version: String,
    val license: String,
    val url: String,
)

private val LIBRARIES = listOf(
    LibraryInfo("libssh", "0.11.4", "LGPL-2.1+", "https://www.libssh.org"),
    LibraryInfo("mbedTLS", "3.6.4", "Apache-2.0", "https://tls.mbed.org"),
    LibraryInfo("ConnectBot termlib", "0.0.24", "Apache-2.0", "https://connectbot.org"),
    LibraryInfo("Jetpack Compose", "2026.03.01 BOM", "Apache-2.0", "https://developer.android.com/compose"),
    LibraryInfo("Hilt (Dagger)", "2.59.2", "Apache-2.0", "https://dagger.dev/hilt"),
    LibraryInfo("Room", "2.8.4", "Apache-2.0", "https://developer.android.com/training/data-storage/room"),
    LibraryInfo("Kotlin", "2.3.20", "Apache-2.0", "https://kotlinlang.org"),
    LibraryInfo("Kotlin Coroutines", "1.10.2", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    LibraryInfo("Tink", "1.15.0", "Apache-2.0", "https://github.com/google/tink"),
    LibraryInfo("AndroidX DataStore", "1.1.3", "Apache-2.0", "https://developer.android.com/topic/libraries/architecture/datastore"),
    LibraryInfo("AndroidX Biometric", "1.2.0-alpha05", "Apache-2.0", "https://developer.android.com/training/sign-in/biometric-auth"),
    LibraryInfo("Material Design 3", "2026.03.01", "Apache-2.0", "https://m3.material.io"),
    LibraryInfo("AndroidX Navigation", "2.9.7", "Apache-2.0", "https://developer.android.com/guide/navigation"),
    LibraryInfo("MockK", "1.13.13", "Apache-2.0", "https://mockk.io"),
    LibraryInfo("Apache MINA SSHD", "2.14.0", "Apache-2.0", "https://mina.apache.org/sshd-project"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(UiR.string.settings_licenses)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Text(
                "ShellDroid",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp),
            )
            Text(
                "SSH client for Android\nLicencia: GPLv3\n\n" +
                    "Copyright © 2026 ShellDroid Dev\n" +
                    "Código fuente disponible bajo los términos de la " +
                    "GNU General Public License v3.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                "Componentes de código abierto",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            LIBRARIES.forEach { lib ->
                ListItem(
                    headlineContent = {
                        Text("${lib.name} ${lib.version}")
                    },
                    supportingContent = {
                        Text("${lib.license} · ${lib.url}")
                    },
                )
            }
        }
    }
}
