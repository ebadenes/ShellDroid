package io.shelldroid.feature.portforward

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.shelldroid.core.db.PortForwardType
import io.shelldroid.core.db.dao.HostDao
import io.shelldroid.core.db.entities.Host
import io.shelldroid.core.db.entities.PortForward
import io.shelldroid.feature.portforward.data.PortForwardRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PortForwardEditViewModel @Inject constructor(
    private val repo: PortForwardRepository,
    private val hostDao: HostDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class FormState(
        val id: String? = null,
        val hostId: String? = null,
        val type: PortForwardType = PortForwardType.LOCAL,
        val sourcePort: String = "",
        val destHost: String = "",
        val destPort: String = "",
        val autoStart: Boolean = false,
        val saving: Boolean = false,
        val saved: Boolean = false,
        val error: String? = null,
    ) {
        val sourcePortError: String?
            get() {
                if (sourcePort.isBlank()) return "Requerido"
                val n = sourcePort.toIntOrNull() ?: return "Número inválido"
                if (n !in 1..65_535) return "Rango 1..65535"
                return null
            }

        val destHostError: String?
            get() = if (type != PortForwardType.DYNAMIC && destHost.isBlank()) "Requerido" else null

        val destPortError: String?
            get() {
                if (type == PortForwardType.DYNAMIC) return null
                if (destPort.isBlank()) return "Requerido"
                val n = destPort.toIntOrNull() ?: return "Número inválido"
                if (n !in 1..65_535) return "Rango 1..65535"
                return null
            }

        val isValid: Boolean
            get() = hostId != null &&
                sourcePortError == null &&
                destHostError == null &&
                destPortError == null
    }

    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> = _form.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val hosts: StateFlow<List<Host>> = flow { emit(repo.currentUserId()) }
        .flatMapLatest { hostDao.observeAll(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        val id: String? = savedStateHandle["id"]
        if (!id.isNullOrEmpty()) load(id)
    }

    fun load(portForwardId: String) {
        viewModelScope.launch {
            val pf = repo.findById(portForwardId) ?: return@launch
            _form.value = FormState(
                id = pf.id,
                hostId = pf.hostId,
                type = pf.type,
                sourcePort = pf.localPort.toString(),
                destHost = pf.remoteHost.orEmpty(),
                destPort = pf.remotePort?.toString().orEmpty(),
                autoStart = pf.autoStart,
            )
        }
    }

    fun onHost(hostId: String?) { _form.value = _form.value.copy(hostId = hostId) }
    fun onType(type: PortForwardType) { _form.value = _form.value.copy(type = type) }
    fun onSourcePort(v: String) {
        _form.value = _form.value.copy(sourcePort = v.filter { it.isDigit() })
    }
    fun onDestHost(v: String) { _form.value = _form.value.copy(destHost = v) }
    fun onDestPort(v: String) {
        _form.value = _form.value.copy(destPort = v.filter { it.isDigit() })
    }
    fun onAutoStart(v: Boolean) { _form.value = _form.value.copy(autoStart = v) }

    fun save() {
        val s = _form.value
        if (!s.isValid) {
            _form.value = s.copy(error = "Revisá los campos del formulario")
            return
        }
        viewModelScope.launch {
            _form.value = s.copy(saving = true, error = null)
            try {
                val dynamic = s.type == PortForwardType.DYNAMIC
                val pf = PortForward(
                    id = s.id ?: UUID.randomUUID().toString(),
                    userId = repo.currentUserId(),
                    hostId = s.hostId!!,
                    type = s.type,
                    localPort = s.sourcePort.toInt(),
                    remoteHost = if (dynamic) null else s.destHost.trim(),
                    remotePort = if (dynamic) null else s.destPort.toInt(),
                    autoStart = s.autoStart,
                    createdAt = System.currentTimeMillis(),
                )
                repo.save(pf)
                _form.value = _form.value.copy(saving = false, saved = true)
            } catch (t: Throwable) {
                _form.value = _form.value.copy(saving = false, error = t.message)
            }
        }
    }
}
