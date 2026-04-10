package io.shelldroid.feature.portforward

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.shelldroid.core.ui.R as UiR
import io.shelldroid.core.db.PortForwardType
import io.shelldroid.core.db.entities.PortForward
import io.shelldroid.core.ssh.ForwardState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortForwardsScreen(
    onAdd: () -> Unit = {},
    onEdit: (String) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: PortForwardsListViewModel = hiltViewModel(),
) {
    val grouped by viewModel.grouped.collectAsState()
    val statuses by viewModel.forwardStatuses.collectAsState()
    var pfToDelete by remember { mutableStateOf<PortForward?>(null) }

    if (pfToDelete != null) {
        AlertDialog(
            onDismissRequest = { pfToDelete = null },
            title = { Text(stringResource(UiR.string.delete)) },
            text = { Text(stringResource(UiR.string.confirm_delete, labelFor(pfToDelete!!))) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(pfToDelete!!)
                    pfToDelete = null
                }) { Text(stringResource(UiR.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pfToDelete = null }) {
                    Text(stringResource(UiR.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(UiR.string.port_forwards)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add port forward")
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (grouped.isEmpty()) {
                Text(
                    stringResource(UiR.string.no_portforwards),
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    grouped.forEach { group ->
                        item(key = "header-${group.host?.id ?: "unknown"}") {
                            Text(
                                text = group.host?.name ?: stringResource(UiR.string.unknown_host),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                            )
                        }
                        items(group.forwards, key = { it.id }) { pf ->
                            val status = statuses[pf.id]
                            PortForwardCard(
                                pf = pf,
                                forwardState = status?.state ?: ForwardState.STOPPED,
                                onClick = { onEdit(pf.id) },
                                onClone = { viewModel.clone(pf) },
                                onDelete = { pfToDelete = pf },
                                onToggle = {
                                    if (status?.state == ForwardState.ACTIVE) {
                                        viewModel.stopForward(pf)
                                    } else {
                                        viewModel.startForward(pf)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PortForwardCard(
    pf: PortForward,
    forwardState: ForwardState,
    onClick: () -> Unit,
    onClone: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypeChip(pf.type)
            // Status indicator dot
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when (forwardState) {
                            ForwardState.ACTIVE -> Color(0xFF4CAF50) // green
                            ForwardState.STARTING -> Color(0xFFFFC107) // amber
                            ForwardState.ERROR -> Color(0xFFF44336) // red
                            ForwardState.STOPPED -> Color(0xFF9E9E9E) // grey
                        }
                    )
            )
            Column(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f),
            ) {
                Text(
                    text = labelFor(pf),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = forwardingString(pf),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // Start/Stop toggle (only for LOCAL type)
            if (pf.type == PortForwardType.LOCAL) {
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (forwardState == ForwardState.ACTIVE)
                            Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (forwardState == ForwardState.ACTIVE)
                            "Stop" else "Start",
                        tint = if (forwardState == ForwardState.ACTIVE)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onClone) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Clone")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun TypeChip(type: PortForwardType) {
    val (label, bg) = when (type) {
        PortForwardType.LOCAL -> "L" to MaterialTheme.colorScheme.primaryContainer
        PortForwardType.REMOTE -> "R" to MaterialTheme.colorScheme.secondaryContainer
        PortForwardType.DYNAMIC -> "D" to MaterialTheme.colorScheme.tertiaryContainer
    }
    Surface(color = bg, shape = MaterialTheme.shapes.small) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun labelFor(pf: PortForward): String = when (pf.type) {
    PortForwardType.LOCAL -> "Local :${pf.localPort}"
    PortForwardType.REMOTE -> "Remote :${pf.localPort}"
    PortForwardType.DYNAMIC -> "SOCKS :${pf.localPort}"
}

private fun forwardingString(pf: PortForward): String = when (pf.type) {
    PortForwardType.LOCAL ->
        "localhost:${pf.localPort} \u2192 ${pf.remoteHost}:${pf.remotePort}"
    PortForwardType.REMOTE ->
        "remote:${pf.localPort} \u2192 ${pf.remoteHost}:${pf.remotePort}"
    PortForwardType.DYNAMIC ->
        "SOCKS on localhost:${pf.localPort}"
}

