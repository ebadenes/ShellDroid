package io.shelldroid.feature.hosts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.shelldroid.core.db.entities.Host
import io.shelldroid.core.db.entities.Identity
import io.shelldroid.feature.hosts.data.HostRepository
import io.shelldroid.feature.hosts.data.IdentityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HostEditViewModel @Inject constructor(
    private val repo: HostRepository,
    private val identityRepo: IdentityRepository,
) : ViewModel() {

    data class FormState(
        val id: String? = null,
        val name: String = "",
        val hostname: String = "",
        val port: String = "22",
        val username: String = "",
        val identityId: String? = null,
        val saving: Boolean = false,
        val saved: Boolean = false,
        val error: String? = null,
    ) {
        val isValid: Boolean
            get() = name.isNotBlank() &&
                hostname.isNotBlank() &&
                username.isNotBlank() &&
                (port.toIntOrNull()?.let { it in 1..65_535 } == true)
    }

    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> = _form.asStateFlow()

    val identities: StateFlow<List<Identity>> = identityRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun load(hostId: String) {
        viewModelScope.launch {
            val h = repo.findById(hostId) ?: return@launch
            _form.value = FormState(
                id = h.id,
                name = h.name,
                hostname = h.hostname,
                port = h.port.toString(),
                username = h.username,
                identityId = h.identityId,
            )
        }
    }

    fun onName(v: String) { _form.value = _form.value.copy(name = v) }
    fun onHostname(v: String) { _form.value = _form.value.copy(hostname = v) }
    fun onPort(v: String) { _form.value = _form.value.copy(port = v.filter { it.isDigit() }) }
    fun onUsername(v: String) { _form.value = _form.value.copy(username = v) }
    fun onIdentity(id: String?) { _form.value = _form.value.copy(identityId = id) }

    fun save() {
        val s = _form.value
        if (!s.isValid) {
            _form.value = s.copy(error = "Revisá los campos del formulario")
            return
        }
        viewModelScope.launch {
            _form.value = s.copy(saving = true, error = null)
            try {
                val now = System.currentTimeMillis()
                val host = Host(
                    id = s.id ?: UUID.randomUUID().toString(),
                    userId = repo.currentUserId(),
                    name = s.name.trim(),
                    hostname = s.hostname.trim(),
                    port = s.port.toInt(),
                    username = s.username.trim(),
                    identityId = s.identityId,
                    createdAt = now,
                )
                repo.upsert(host)
                _form.value = _form.value.copy(saving = false, saved = true)
            } catch (t: Throwable) {
                _form.value = _form.value.copy(saving = false, error = t.message)
            }
        }
    }
}
