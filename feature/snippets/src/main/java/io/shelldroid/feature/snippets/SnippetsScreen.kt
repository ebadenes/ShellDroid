package io.shelldroid.feature.snippets

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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.shelldroid.core.ui.R as UiR
import io.shelldroid.core.db.entities.Snippet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetsScreen(
    onAddSnippet: () -> Unit = {},
    onEditSnippet: (String) -> Unit = {},
    onOpenTerminal: (String) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: SnippetsListViewModel = hiltViewModel(),
) {
    val snippets by viewModel.snippets.collectAsState()
    val activeSessions by viewModel.activeSessions.collectAsState()
    var snippetToDelete by remember { mutableStateOf<Snippet?>(null) }
    var snippetToRun by remember { mutableStateOf<Snippet?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noSessionsMsg = stringResource(UiR.string.snippet_no_active_session)

    LaunchedEffect(Unit) { viewModel.refreshActiveSessions() }

    // Session picker: one active → run immediately; many → show picker;
    // zero → snackbar hint.
    if (snippetToRun != null) {
        val s = snippetToRun!!
        when {
            activeSessions.isEmpty() -> {
                LaunchedEffect(s.id) {
                    snackbarHostState.showSnackbar(noSessionsMsg)
                    snippetToRun = null
                }
            }
            activeSessions.size == 1 -> {
                LaunchedEffect(s.id) {
                    val session = activeSessions.first()
                    viewModel.dispatchToSession(session.hostId, s)
                    snippetToRun = null
                    onOpenTerminal(session.hostId)
                }
            }
            else -> {
                AlertDialog(
                    onDismissRequest = { snippetToRun = null },
                    title = { Text(stringResource(UiR.string.snippet_choose_session)) },
                    text = {
                        Column {
                            activeSessions.forEach { session ->
                                TextButton(
                                    onClick = {
                                        viewModel.dispatchToSession(session.hostId, s)
                                        snippetToRun = null
                                        onOpenTerminal(session.hostId)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(session.label) }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { snippetToRun = null }) {
                            Text(stringResource(UiR.string.cancel))
                        }
                    },
                )
            }
        }
    }

    if (snippetToDelete != null) {
        AlertDialog(
            onDismissRequest = { snippetToDelete = null },
            title = { Text(stringResource(UiR.string.delete)) },
            text = { Text(stringResource(UiR.string.confirm_delete, snippetToDelete!!.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(snippetToDelete!!)
                    snippetToDelete = null
                }) { Text(stringResource(UiR.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { snippetToDelete = null }) {
                    Text(stringResource(UiR.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(UiR.string.snippets)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddSnippet) {
                Icon(Icons.Default.Add, contentDescription = "Add snippet")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (snippets.isEmpty()) {
                Text(
                    stringResource(UiR.string.no_snippets),
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(snippets, key = { it.id }) { snippet ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        snippet.name,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        snippet.command,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = { viewModel.clone(snippet) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Clone")
                                }
                                IconButton(onClick = { snippetToRun = snippet }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Run")
                                }
                                IconButton(onClick = { onEditSnippet(snippet.id) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                                }
                                IconButton(onClick = { snippetToDelete = snippet }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
