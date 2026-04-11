package io.shelldroid.feature.portforward

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.shelldroid.core.ui.R as UiR
import io.shelldroid.core.db.PortForwardType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortForwardEditScreen(
    portForwardId: String? = null,
    onDone: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: PortForwardEditViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsState()
    val hosts by viewModel.hosts.collectAsState()
    var hostDropdownOpen by remember { mutableStateOf(false) }

    LaunchedEffect(portForwardId) {
        if (portForwardId != null) viewModel.load(portForwardId)
    }
    LaunchedEffect(form.saved) { if (form.saved) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (portForwardId == null) stringResource(UiR.string.new_portforward) else stringResource(UiR.string.edit_portforward))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
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
            // Host dropdown
            ExposedDropdownMenuBox(
                expanded = hostDropdownOpen,
                onExpandedChange = { hostDropdownOpen = !hostDropdownOpen },
            ) {
                val selected = hosts.firstOrNull { it.id == form.hostId }
                OutlinedTextField(
                    readOnly = true,
                    value = selected?.name ?: stringResource(UiR.string.choose_host),
                    onValueChange = {},
                    label = { Text(stringResource(UiR.string.host)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = hostDropdownOpen)
                    },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                DropdownMenu(
                    expanded = hostDropdownOpen,
                    onDismissRequest = { hostDropdownOpen = false },
                ) {
                    hosts.forEach { h ->
                        DropdownMenuItem(
                            text = { Text(h.name) },
                            onClick = {
                                viewModel.onHost(h.id)
                                hostDropdownOpen = false
                            },
                        )
                    }
                }
            }

            // Type segmented
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val m = Modifier.weight(1f)
                TypeButton(m, "LOCAL", form.type == PortForwardType.LOCAL) {
                    viewModel.onType(PortForwardType.LOCAL)
                }
                // REMOTE forwarding is post-v1 — disabled with a "coming
                // soon" label so users see it is planned.
                TypeButton(
                    modifier = m,
                    label = stringResource(UiR.string.port_forward_remote_soon),
                    selected = form.type == PortForwardType.REMOTE,
                    enabled = false,
                    onClick = {},
                )
                TypeButton(m, "DYNAMIC", form.type == PortForwardType.DYNAMIC) {
                    viewModel.onType(PortForwardType.DYNAMIC)
                }
            }

            OutlinedTextField(
                value = form.sourcePort,
                onValueChange = viewModel::onSourcePort,
                label = { Text(stringResource(UiR.string.source_port)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = form.sourcePortError != null,
                supportingText = {
                    form.sourcePortError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            if (form.type != PortForwardType.DYNAMIC) {
                OutlinedTextField(
                    value = form.destHost,
                    onValueChange = viewModel::onDestHost,
                    label = { Text(stringResource(UiR.string.dest_host)) },
                    isError = form.destHostError != null,
                    supportingText = {
                        form.destHostError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = form.destPort,
                    onValueChange = viewModel::onDestPort,
                    label = { Text(stringResource(UiR.string.dest_port)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = form.destPortError != null,
                    supportingText = {
                        form.destPortError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    },
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
                Text(if (form.saving) stringResource(UiR.string.saving) else stringResource(UiR.string.save))
            }
        }
    }
}

@Composable
private fun TypeButton(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier, enabled = enabled) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) { Text(label) }
    }
}
