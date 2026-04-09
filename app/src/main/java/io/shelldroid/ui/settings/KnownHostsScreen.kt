package io.shelldroid.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.shelldroid.core.db.entities.KnownHost
import io.shelldroid.core.ui.R as UiR
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnownHostsScreen(
    onBack: () -> Unit,
    viewModel: KnownHostsViewModel = hiltViewModel(),
) {
    val knownHosts by viewModel.knownHosts.collectAsState()
    var hostToDelete by remember { mutableStateOf<KnownHost?>(null) }

    if (hostToDelete != null) {
        AlertDialog(
            onDismissRequest = { hostToDelete = null },
            title = { Text(stringResource(UiR.string.delete)) },
            text = {
                Text(
                    stringResource(
                        UiR.string.confirm_delete,
                        "${hostToDelete!!.hostname}:${hostToDelete!!.port}",
                    )
                )
            },
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(UiR.string.known_hosts)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(UiR.string.back))
                    }
                },
            )
        },
    ) { inner ->
        if (knownHosts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(UiR.string.no_known_hosts),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
            ) {
                items(knownHosts, key = { it.id }) { kh ->
                    KnownHostRow(
                        knownHost = kh,
                        onDelete = { hostToDelete = kh },
                    )
                }
            }
        }
    }
}

@Composable
private fun KnownHostRow(
    knownHost: KnownHost,
    onDelete: () -> Unit,
) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val lastSeenFormatted = remember(knownHost.lastSeen) {
        dateFormat.format(Date(knownHost.lastSeen))
    }
    val truncatedFingerprint = remember(knownHost.fingerprintSha256) {
        val fp = knownHost.fingerprintSha256
        if (fp.length > 24) fp.take(24) + "..." else fp
    }

    ListItem(
        headlineContent = {
            Text(
                text = "${knownHost.hostname}:${knownHost.port}",
                fontWeight = FontWeight.Bold,
            )
        },
        supportingContent = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = knownHost.keyType,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "SHA-256: $truncatedFingerprint",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(UiR.string.last_seen, lastSeenFormatted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(UiR.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}
