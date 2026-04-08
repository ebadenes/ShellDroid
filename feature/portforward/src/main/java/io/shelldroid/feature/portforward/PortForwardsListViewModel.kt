package io.shelldroid.feature.portforward

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.shelldroid.core.db.dao.HostDao
import io.shelldroid.core.db.entities.Host
import io.shelldroid.core.db.entities.PortForward
import io.shelldroid.feature.portforward.data.PortForwardRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PortForwardsListViewModel @Inject constructor(
    private val repo: PortForwardRepository,
    private val hostDao: HostDao,
) : ViewModel() {

    data class Grouped(
        val host: Host?,
        val forwards: List<PortForward>,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val hostsFlow: Flow<List<Host>> =
        // We reuse repo.currentUserId by piggybacking on observeAll which already scopes by user;
        // for the host list we observe via HostDao using the same user id lookup.
        kotlinx.coroutines.flow.flow {
            emit(repo.currentUserId())
        }.flatMapLatest { userId -> hostDao.observeAll(userId) }

    val forwards: StateFlow<List<PortForward>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val grouped: StateFlow<List<Grouped>> = combine(forwards, hostsFlow) { pfs, hosts ->
        val byId = hosts.associateBy { it.id }
        pfs.groupBy { it.hostId }
            .map { (hostId, list) -> Grouped(byId[hostId], list) }
            .sortedBy { it.host?.name ?: "" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(pf: PortForward) {
        viewModelScope.launch { repo.delete(pf) }
    }
}
