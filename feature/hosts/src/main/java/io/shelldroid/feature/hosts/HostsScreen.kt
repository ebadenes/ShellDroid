package io.shelldroid.feature.hosts

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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.res.stringResource
import io.shelldroid.core.ui.R as UiR
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    var hostToDelete by remember { mutableStateOf<Host?>(null) }

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

    LaunchedEffect(connectState) {
        when (val s = connectState) {
            is HostsListViewModel.ConnectState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.resetConnectState()
            }
            is HostsListViewModel.ConnectState.Connected -> {
                snackbarHostState.showSnackbar("Conectado")
                viewModel.resetConnectState()
            }
            else -> Unit
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
                    IconButton(onClick = onOpenSnippets) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Snippets")
                    }
                    IconButton(onClick = onOpenPortForwards) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Port forwards")
                    }
                    IconButton(onClick = onOpenIdentities) {
                        Icon(Icons.Default.Person, contentDescription = "Identities")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                                Column(modifier = Modifier.weight(1f)) {
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
                                    IconButton(onClick = {
                                        clipboardManager.setText(AnnotatedString("${host.username}@${host.hostname}:${host.port}"))
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(20.dp))
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
