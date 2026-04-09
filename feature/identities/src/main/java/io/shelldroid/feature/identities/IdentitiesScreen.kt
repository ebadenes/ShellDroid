package io.shelldroid.feature.identities

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.shelldroid.core.db.entities.Identity
import io.shelldroid.core.ui.R as UiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentitiesScreen(
    onAddIdentity: () -> Unit = {},
    onEditIdentity: (String) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: IdentitiesListViewModel = hiltViewModel(),
) {
    val identities by viewModel.identities.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    var identityToDelete by remember { mutableStateOf<Identity?>(null) }

    if (identityToDelete != null) {
        AlertDialog(
            onDismissRequest = { identityToDelete = null },
            title = { Text(stringResource(UiR.string.delete)) },
            text = { Text(stringResource(UiR.string.confirm_delete, identityToDelete!!.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(identityToDelete!!)
                    identityToDelete = null
                }) { Text(stringResource(UiR.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { identityToDelete = null }) {
                    Text(stringResource(UiR.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(UiR.string.identities)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddIdentity) {
                Icon(Icons.Default.Add, contentDescription = "Add identity")
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (identities.isEmpty()) {
                Text(
                    stringResource(UiR.string.no_identities),
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(identities, key = { it.id }) { identity ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(identity.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        identity.authType.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (identity.needsReentry) {
                                        Text(
                                            "\u26A0 Re-ingres\u00E1 la credencial",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(identity.name))
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { onEditIdentity(identity.id) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { identityToDelete = identity }) {
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
