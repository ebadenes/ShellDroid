package io.shelldroid.feature.snippets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.shelldroid.core.db.entities.Snippet
import io.shelldroid.feature.snippets.data.SnippetRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SnippetsListViewModel @Inject constructor(
    private val repo: SnippetRepository,
) : ViewModel() {

    val snippets: StateFlow<List<Snippet>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
