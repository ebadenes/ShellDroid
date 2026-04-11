package io.shelldroid.feature.snippets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.shelldroid.core.db.dao.HostDao
import io.shelldroid.core.db.entities.Snippet
import io.shelldroid.core.ssh.PendingAutoCommand
import io.shelldroid.core.ssh.SshSessionManager
import io.shelldroid.feature.snippets.data.SnippetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ActiveSession(val hostId: String, val label: String)

@HiltViewModel
class SnippetsListViewModel @Inject constructor(
    private val repo: SnippetRepository,
    private val sessionManager: SshSessionManager,
    private val hostDao: HostDao,
) : ViewModel() {

    val snippets: StateFlow<List<Snippet>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activeSessions = MutableStateFlow<List<ActiveSession>>(emptyList())
    /**
     * Snapshot of active SSH sessions — id + "name (user@host)" label.
     * Refreshed via [refreshActiveSessions] because SshSessionManager does
     * not expose a reactive view of its internal map.
     */
    val activeSessions: StateFlow<List<ActiveSession>> = _activeSessions.asStateFlow()

    fun refreshActiveSessions() {
        viewModelScope.launch {
            val snapshot = sessionManager.activeHostIds().mapNotNull { hostId ->
                val host = hostDao.findById(hostId) ?: return@mapNotNull null
                val niceName = host.name.ifBlank { "${host.username}@${host.hostname}" }
                ActiveSession(hostId, niceName)
            }
            _activeSessions.value = snapshot
        }
    }

    /**
     * Queue [snippet] for the terminal attached to [hostId]. The terminal
     * composable drains [PendingAutoCommand] via a `repeatOnLifecycle`
     * block, so the command is consumed the next time that host's
     * terminal screen becomes STARTED — which happens immediately when
     * we navigate to it from the snippets screen.
     */
    fun dispatchToSession(hostId: String, snippet: Snippet) {
        PendingAutoCommand.set(hostId, snippet.command)
    }

    fun delete(snippet: Snippet) {
        viewModelScope.launch { repo.delete(snippet) }
    }

    fun clone(snippet: Snippet) {
        viewModelScope.launch {
            val copy = snippet.copy(
                id = UUID.randomUUID().toString(),
                name = "${snippet.name} (copia)",
                createdAt = System.currentTimeMillis(),
            )
            repo.upsert(copy)
        }
    }
}
