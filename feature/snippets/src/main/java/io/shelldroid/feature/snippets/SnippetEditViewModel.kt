package io.shelldroid.feature.snippets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.shelldroid.core.db.entities.Snippet
import io.shelldroid.feature.snippets.data.SnippetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SnippetEditViewModel @Inject constructor(
    private val repo: SnippetRepository,
) : ViewModel() {

    data class FormState(
        val id: String? = null,
        val name: String = "",
        val command: String = "",
        val saving: Boolean = false,
        val saved: Boolean = false,
        val error: String? = null,
    ) {
        val isValid: Boolean
            get() = name.isNotBlank() && command.isNotBlank()
    }

    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> = _form.asStateFlow()

    fun load(snippetId: String) {
        viewModelScope.launch {
            val s = repo.findById(snippetId) ?: return@launch
            _form.value = FormState(
                id = s.id,
                name = s.name,
                command = s.command,
            )
        }
    }

    fun onName(v: String) { _form.value = _form.value.copy(name = v) }
    fun onCommand(v: String) { _form.value = _form.value.copy(command = v) }

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
                val snippet = Snippet(
                    id = s.id ?: UUID.randomUUID().toString(),
                    userId = repo.currentUserId(),
                    name = s.name.trim(),
                    command = s.command,
                    description = null,
                    createdAt = now,
                )
                repo.upsert(snippet)
                _form.value = _form.value.copy(saving = false, saved = true)
            } catch (t: Throwable) {
                _form.value = _form.value.copy(saving = false, error = t.message)
            }
        }
    }
}
