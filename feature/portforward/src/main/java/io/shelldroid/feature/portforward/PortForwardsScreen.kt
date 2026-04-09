package io.shelldroid.feature.portforward

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.shelldroid.core.ui.R as UiR
import io.shelldroid.core.db.PortForwardType
import io.shelldroid.core.db.entities.PortForward

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortForwardsScreen(
    onAdd: () -> Unit = {},
    onEdit: (String) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: PortForwardsListViewModel = hiltViewModel(),
) {
    val grouped by viewModel.grouped.collectAsState()

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
                            PortForwardCard(
                                pf = pf,
                                onClick = { onEdit(pf.id) },
                                onDelete = { viewModel.delete(pf) },
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
    onClick: () -> Unit,
    onDelete: () -> Unit,
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
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
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

