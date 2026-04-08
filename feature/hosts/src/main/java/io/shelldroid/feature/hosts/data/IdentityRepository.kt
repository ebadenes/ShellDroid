package io.shelldroid.feature.hosts.data

import io.shelldroid.core.db.CurrentUserProvider
import io.shelldroid.core.db.dao.IdentityDao
import io.shelldroid.core.db.entities.Identity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IdentityRepository @Inject constructor(
    private val dao: IdentityDao,
    private val currentUser: CurrentUserProvider,
) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeAll(): Flow<List<Identity>> =
        currentUser.observeCurrent().flatMapLatest { dao.observeAll(it.id) }

    suspend fun findById(id: String): Identity? = dao.findById(id)

    suspend fun upsert(identity: Identity) = dao.upsert(identity)

    suspend fun delete(identity: Identity) = dao.delete(identity)

    suspend fun currentUserId(): String = currentUser.current().id
}
