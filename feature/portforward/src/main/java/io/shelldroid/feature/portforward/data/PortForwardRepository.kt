package io.shelldroid.feature.portforward.data

import io.shelldroid.core.db.CurrentUserProvider
import io.shelldroid.core.db.dao.PortForwardDao
import io.shelldroid.core.db.entities.PortForward
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

// TODO wire to libssh port_forward when ssh-native exposes the bindings
@Singleton
class PortForwardRepository @Inject constructor(
    private val dao: PortForwardDao,
    private val currentUser: CurrentUserProvider,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAll(): Flow<List<PortForward>> =
        currentUser.observeCurrent().flatMapLatest { dao.observeAll(it.id) }

    fun observeForHost(hostId: String): Flow<List<PortForward>> = dao.observeByHost(hostId)

    suspend fun findById(id: String): PortForward? = dao.findById(id)

    suspend fun save(pf: PortForward) = dao.upsert(pf)

    suspend fun delete(pf: PortForward) = dao.delete(pf)

    suspend fun currentUserId(): String = currentUser.current().id
}
