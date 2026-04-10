package io.shelldroid.feature.portforward

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.shelldroid.core.db.PortForwardType
import io.shelldroid.core.db.dao.HostDao
import io.shelldroid.core.db.entities.Host
import io.shelldroid.core.db.entities.PortForward
import io.shelldroid.core.ssh.ForwardState
import io.shelldroid.core.ssh.ForwardStatus
import io.shelldroid.core.ssh.PortForwardManager
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
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PortForwardsListViewModel @Inject constructor(
    private val repo: PortForwardRepository,
    private val hostDao: HostDao,
    private val portForwardManager: PortForwardManager,
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

    /** Observable statuses of all running/stopped tunnels. */
    val forwardStatuses: StateFlow<Map<String, ForwardStatus>> = portForwardManager.statuses

    fun delete(pf: PortForward) {
        viewModelScope.launch {
            portForwardManager.stop(pf.id)
            repo.delete(pf)
        }
    }

    fun clone(pf: PortForward) {
        viewModelScope.launch {
            val copy = pf.copy(
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
            )
            repo.save(copy)
        }
    }

    /** Starts a LOCAL forward tunnel. REMOTE/DYNAMIC are not yet supported. */
    fun startForward(pf: PortForward) {
        if (pf.type != PortForwardType.LOCAL) return // TODO: REMOTE, DYNAMIC
        val remoteHost = pf.remoteHost ?: return
        val remotePort = pf.remotePort ?: return
        portForwardManager.startLocal(
            forwardId = pf.id,
            hostId = pf.hostId,
            localPort = pf.localPort,
            remoteHost = remoteHost,
            remotePort = remotePort,
        )
    }

    /** Stops a running tunnel. */
    fun stopForward(pf: PortForward) {
        portForwardManager.stop(pf.id)
    }

    /** Checks whether a forward is currently active. */
    fun isForwardActive(forwardId: String): Boolean {
        val status = forwardStatuses.value[forwardId]
        return status?.state == ForwardState.ACTIVE
    }
}
