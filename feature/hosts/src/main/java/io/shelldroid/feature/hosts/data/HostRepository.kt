package io.shelldroid.feature.hosts.data

import io.shelldroid.core.db.CurrentUserProvider
import io.shelldroid.core.db.dao.HostDao
import io.shelldroid.core.db.entities.Host
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HostRepository @Inject constructor(
    private val dao: HostDao,
    private val currentUser: CurrentUserProvider,
) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeAll(): Flow<List<Host>> =
        currentUser.observeCurrent().flatMapLatest { dao.observeAll(it.id) }

    suspend fun findById(id: String): Host? = dao.findById(id)

    suspend fun upsert(host: Host) = dao.upsert(host)

    suspend fun delete(host: Host) = dao.delete(host)

    suspend fun updateLastConnected(id: String, timestamp: Long) =
        dao.updateLastConnectedAt(id, timestamp)

    suspend fun currentUserId(): String = currentUser.current().id
}
