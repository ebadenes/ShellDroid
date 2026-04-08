package io.shelldroid.feature.hosts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostEditScreen(
    hostId: String? = null,
    onDone: () -> Unit = {},
    viewModel: HostEditViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsState()
    val identities by viewModel.identities.collectAsState()
    var dropdownOpen by remember { mutableStateOf(false) }

    LaunchedEffect(hostId) { if (hostId != null) viewModel.load(hostId) }
    LaunchedEffect(form.saved) { if (form.saved) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (hostId == null) "Nuevo host" else "Editar host") },
                actions = { TextButton(onClick = onDone) { Text("Cancelar") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::onName,
                label = { Text("Nombre") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = form.hostname,
                onValueChange = viewModel::onHostname,
                label = { Text("Hostname") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = form.port,
                onValueChange = viewModel::onPort,
                label = { Text("Puerto") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = form.username,
                onValueChange = viewModel::onUsername,
                label = { Text("Usuario") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            ExposedDropdownMenuBox(
                expanded = dropdownOpen,
                onExpandedChange = { dropdownOpen = !dropdownOpen },
            ) {
                val selected = identities.firstOrNull { it.id == form.identityId }
                OutlinedTextField(
                    readOnly = true,
                    value = selected?.name ?: "(sin identity)",
                    onValueChange = {},
                    label = { Text("Identity") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownOpen) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                DropdownMenu(
                    expanded = dropdownOpen,
                    onDismissRequest = { dropdownOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("(sin identity)") },
                        onClick = {
                            viewModel.onIdentity(null)
                            dropdownOpen = false
                        },
                    )
                    identities.forEach { identity ->
                        DropdownMenuItem(
                            text = { Text(identity.name) },
                            onClick = {
                                viewModel.onIdentity(identity.id)
                                dropdownOpen = false
                            },
                        )
                    }
                }
            }

            form.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = viewModel::save,
                enabled = form.isValid && !form.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (form.saving) "Guardando..." else "Guardar")
            }
        }
    }
}
