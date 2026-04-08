package io.shelldroid.feature.snippets.data

import io.shelldroid.core.db.CurrentUserProvider
import io.shelldroid.core.db.dao.SnippetDao
import io.shelldroid.core.db.entities.Snippet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnippetRepository @Inject constructor(
    private val dao: SnippetDao,
    private val currentUser: CurrentUserProvider,
) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeAll(): Flow<List<Snippet>> =
        currentUser.observeCurrent().flatMapLatest { dao.observeAll(it.id) }

    suspend fun findById(id: String): Snippet? = dao.findById(id)

    suspend fun upsert(snippet: Snippet) = dao.upsert(snippet)

    suspend fun delete(snippet: Snippet) = dao.delete(snippet)

    suspend fun currentUserId(): String = currentUser.current().id
}
