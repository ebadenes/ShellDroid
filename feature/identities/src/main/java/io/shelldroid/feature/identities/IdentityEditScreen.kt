package io.shelldroid.feature.identities

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.shelldroid.core.db.AuthType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityEditScreen(
    identityId: String? = null,
    onDone: () -> Unit = {},
    viewModel: IdentityEditViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsState()
    var typeOpen by remember { mutableStateOf(false) }

    LaunchedEffect(identityId) { if (identityId != null) viewModel.load(identityId) }
    LaunchedEffect(form.saved) { if (form.saved) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (identityId == null) "Nueva identity" else "Editar identity") },
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

            ExposedDropdownMenuBox(
                expanded = typeOpen,
                onExpandedChange = { typeOpen = !typeOpen },
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = form.authType.name,
                    onValueChange = {},
                    label = { Text("Tipo") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeOpen) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                DropdownMenu(
                    expanded = typeOpen,
                    onDismissRequest = { typeOpen = false },
                ) {
                    AuthType.values().forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.name) },
                            onClick = {
                                viewModel.onAuthType(t)
                                typeOpen = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = form.secret,
                onValueChange = viewModel::onSecret,
                label = {
                    Text(if (form.authType == AuthType.PASSWORD) "Contraseña" else "Clave privada (PEM)")
                },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = form.authType == AuthType.PASSWORD,
            )

            if (form.authType != AuthType.PASSWORD) {
                OutlinedTextField(
                    value = form.passphrase,
                    onValueChange = viewModel::onPassphrase,
                    label = { Text("Passphrase (opcional)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
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
