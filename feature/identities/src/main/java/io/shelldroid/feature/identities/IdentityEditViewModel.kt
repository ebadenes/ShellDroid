package io.shelldroid.feature.identities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.shelldroid.core.db.AuthType
import io.shelldroid.core.db.entities.Identity
import io.shelldroid.core.security.CredentialVault
import io.shelldroid.feature.hosts.data.IdentityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class IdentityEditViewModel @Inject constructor(
    private val repo: IdentityRepository,
    private val vault: CredentialVault,
) : ViewModel() {

    data class FormState(
        val id: String? = null,
        val name: String = "",
        val authType: AuthType = AuthType.PASSWORD,
        val secret: String = "",
        val passphrase: String = "",
        val createdAt: Long? = null,
        val saving: Boolean = false,
        val saved: Boolean = false,
        val error: String? = null,
    ) {
        val isValid: Boolean
            get() = name.isNotBlank() && secret.isNotBlank()
    }

    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> = _form.asStateFlow()

    fun load(identityId: String) {
        viewModelScope.launch {
            val i = repo.findById(identityId) ?: return@launch
            // Secret is intentionally NOT pre-filled — the user must re-enter it
            // (the vault decrypt-into-CharArray contract forbids holding plaintext
            // in a String-based form field).
            _form.value = FormState(
                id = i.id,
                name = i.name,
                authType = i.authType,
                createdAt = i.createdAt,
            )
        }
    }

    fun onName(v: String) { _form.value = _form.value.copy(name = v) }
    fun onAuthType(v: AuthType) { _form.value = _form.value.copy(authType = v) }
    fun onSecret(v: String) { _form.value = _form.value.copy(secret = v) }
    fun onPassphrase(v: String) { _form.value = _form.value.copy(passphrase = v) }

    fun save() {
        val s = _form.value
        if (!s.isValid) {
            _form.value = s.copy(error = "Nombre y secreto son requeridos")
            return
        }
        viewModelScope.launch {
            _form.value = s.copy(saving = true, error = null)
            try {
                val secretChars = s.secret.toCharArray()
                val encryptedSecret = try {
                    when (s.authType) {
                        AuthType.PASSWORD -> vault.encrypt(secretChars)
                        AuthType.KEY_RSA, AuthType.KEY_ED25519, AuthType.KEY_ECDSA ->
                            vault.encryptBytes(s.secret.toByteArray(Charsets.UTF_8))
                    }
                } finally {
                    secretChars.fill('\u0000')
                }
                val encryptedPassphrase = if (s.passphrase.isNotEmpty()) {
                    val ppChars = s.passphrase.toCharArray()
                    try {
                        vault.encrypt(ppChars)
                    } finally {
                        ppChars.fill('\u0000')
                    }
                } else null

                val identity = Identity(
                    id = s.id ?: UUID.randomUUID().toString(),
                    userId = repo.currentUserId(),
                    name = s.name.trim(),
                    authType = s.authType,
                    encryptedSecret = encryptedSecret,
                    encryptedPassphrase = encryptedPassphrase,
                    needsReentry = false,
                    createdAt = s.createdAt ?: System.currentTimeMillis(),
                )
                repo.upsert(identity)
                _form.value = _form.value.copy(saving = false, saved = true)
            } catch (t: Throwable) {
                _form.value = _form.value.copy(saving = false, error = t.message)
            }
        }
    }
}
