package io.shelldroid.feature.hosts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.res.stringResource
import io.shelldroid.core.ui.R as UiR
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.shelldroid.core.db.entities.Host
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.shelldroid.feature.hosts.tofu.ComposeHostKeyPrompter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostsScreen(
    onAddHost: () -> Unit = {},
    onEditHost: (String) -> Unit = {},
    onOpenIdentities: () -> Unit = {},
    onOpenSnippets: () -> Unit = {},
    onOpenPortForwards: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    prompter: ComposeHostKeyPrompter? = null,
    viewModel: HostsListViewModel = hiltViewModel(),
) {
    val hosts by viewModel.hosts.collectAsState()
    val connectState by viewModel.connectState.collectAsState()
    val activeHostIds by viewModel.activeHostIds.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var hostToDelete by remember { mutableStateOf<Host?>(null) }
    var showQuickConnect by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }

    // Quick Connect dialog — user@host:port + optional password.
    // The host is always saved to DB (so the terminal can resolve it for
    // title / auto-command / identity). If "Save connection" is unchecked
    // it is marked ephemeral and TerminalViewModel deletes it on disconnect.
    if (showQuickConnect) {
        var qcInput by remember { mutableStateOf("") }
        var qcPassword by remember { mutableStateOf("") }
        var qcSave by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showQuickConnect = false },
            title = { Text(stringResource(UiR.string.quick_connect)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = qcInput,
                        onValueChange = { qcInput = it },
                        label = { Text("user@host:port") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("root@192.168.1.1:22") },
                    )
                    OutlinedTextField(
                        value = qcPassword,
                        onValueChange = { qcPassword = it },
                        label = { Text(stringResource(UiR.string.password_optional)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { qcSave = !qcSave },
                    ) {
                        Checkbox(
                            checked = qcSave,
                            onCheckedChange = { qcSave = it },
                        )
                        Text(stringResource(UiR.string.save_connection))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = parseQuickConnect(qcInput.trim())
                        if (parsed != null) {
                            viewModel.quickConnect(
                                parsed.first, parsed.second, parsed.third,
                                qcPassword,
                                saveToDb = qcSave,
                            )
                            showQuickConnect = false
                        }
                    },
                    enabled = qcInput.contains("@"),
                ) { Text(stringResource(UiR.string.connect)) }
            },
            dismissButton = {
                TextButton(onClick = { showQuickConnect = false }) {
                    Text(stringResource(UiR.string.cancel))
                }
            },
        )
    }

    if (hostToDelete != null) {
        AlertDialog(
            onDismissRequest = { hostToDelete = null },
            title = { Text(stringResource(UiR.string.delete)) },
            text = { Text(stringResource(UiR.string.confirm_delete, hostToDelete!!.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(hostToDelete!!)
                    hostToDelete = null
                }) { Text(stringResource(UiR.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { hostToDelete = null }) {
                    Text(stringResource(UiR.string.cancel))
                }
            },
        )
    }

    // Credentials prompt for hosts without an identity yet. Lets the user
    // either pick an existing identity (SSH key) or type a one-off password.
    val needsPasswordState = connectState as? HostsListViewModel.ConnectState.NeedsPassword
    if (needsPasswordState != null) {
        val identities by viewModel.identities.collectAsState()
        var usePassword by remember { mutableStateOf(true) }
        var pwInput by remember { mutableStateOf("") }
        var selectedIdentityId by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { viewModel.resetConnectState() },
            title = { Text(stringResource(UiR.string.connect)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (identities.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = usePassword,
                                onClick = { usePassword = true },
                                label = { Text(stringResource(UiR.string.use_password)) },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FilterChip(
                                selected = !usePassword,
                                onClick = { usePassword = false },
                                label = { Text(stringResource(UiR.string.use_identity)) },
                            )
                        }
                    }
                    if (usePassword || identities.isEmpty()) {
                        OutlinedTextField(
                            value = pwInput,
                            onValueChange = { pwInput = it },
                            label = { Text(stringResource(UiR.string.password)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    } else {
                        Text(
                            text = stringResource(UiR.string.select_identity),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        identities.forEach { identity ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedIdentityId = identity.id }
                                    .padding(vertical = 4.dp),
                            ) {
                                RadioButton(
                                    selected = selectedIdentityId == identity.id,
                                    onClick = { selectedIdentityId = identity.id },
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(identity.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (usePassword || identities.isEmpty()) {
                            viewModel.connectWithPassword(needsPasswordState.hostId, pwInput)
                        } else {
                            val id = selectedIdentityId ?: return@TextButton
                            viewModel.connectWithIdentity(needsPasswordState.hostId, id)
                        }
                    },
                    enabled = if (usePassword || identities.isEmpty()) pwInput.isNotEmpty()
                              else selectedIdentityId != null,
                ) { Text(stringResource(UiR.string.connect)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resetConnectState() }) {
                    Text(stringResource(UiR.string.cancel))
                }
            },
        )
    }

    LaunchedEffect(connectState) {
        // Note: navigation to the terminal on Connected is handled by
        // ShellDroidNavHost (which hoists this VM and observes the same
        // state). Resetting here would race with that effect — so we
        // only handle Error locally and let NavHost reset on Connected.
        val s = connectState
        if (s is HostsListViewModel.ConnectState.Error) {
            snackbarHostState.showSnackbar(s.message)
            viewModel.resetConnectState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Logo prompt icon
                        Text(
                            ">_",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ShellDroid")
                    }
                },
                actions = {
                    IconButton(onClick = { showQuickConnect = true }) {
                        Icon(Icons.Default.FlashOn, contentDescription = stringResource(UiR.string.quick_connect))
                    }
                    Box {
                        IconButton(onClick = { overflowExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(UiR.string.snippets)) },
                                onClick = { overflowExpanded = false; onOpenSnippets() },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(UiR.string.port_forwards)) },
                                onClick = { overflowExpanded = false; onOpenPortForwards() },
                                leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(UiR.string.identities)) },
                                onClick = { overflowExpanded = false; onOpenIdentities() },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(UiR.string.settings)) },
                                onClick = { overflowExpanded = false; onOpenSettings() },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddHost) {
                Icon(Icons.Default.Add, contentDescription = "Add host")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (hosts.isEmpty()) {
                Text(
                    stringResource(UiR.string.no_hosts),
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(hosts, key = { it.id }) { host ->
                        val isActive = host.id in activeHostIds
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.connect(host.id) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Connected indicator dot — same palette as
                                // the port-forward status dots for consistency.
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isActive) Color(0xFF4CAF50) // green
                                            else Color(0xFF9E9E9E),         // grey
                                        ),
                                )
                                Column(
                                    modifier = Modifier
                                        .padding(start = 10.dp)
                                        .weight(1f),
                                ) {
                                    Text(host.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${host.username}@${host.hostname}:${host.port}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                val connecting = (connectState as? HostsListViewModel.ConnectState.Connecting)
                                    ?.hostId == host.id
                                if (connecting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    IconButton(onClick = { viewModel.clone(host) }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Clone", modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { onEditHost(host.id) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { hostToDelete = host }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Optional TOFU dialog rendering — feature modules render the prompts when wired in.
    if (prompter != null) {
        HostKeyDialogHost(prompter)
    }
}

/**
 * Parse "user@host:port" → Triple(hostname, port, username).
 * Port is optional (defaults to 22). Username is required.
 */
private fun parseQuickConnect(input: String): Triple<String, Int, String>? {
    val atIdx = input.indexOf('@')
    if (atIdx < 1) return null
    val user = input.substring(0, atIdx)
    val rest = input.substring(atIdx + 1)
    val colonIdx = rest.indexOf(':')
    return if (colonIdx >= 0) {
        val host = rest.substring(0, colonIdx)
        val port = rest.substring(colonIdx + 1).toIntOrNull() ?: 22
        Triple(host, port, user)
    } else {
        Triple(rest, 22, user)
    }
}
