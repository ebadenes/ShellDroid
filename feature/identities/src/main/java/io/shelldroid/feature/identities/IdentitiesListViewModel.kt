package io.shelldroid.feature.identities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.shelldroid.core.db.entities.Identity
import io.shelldroid.feature.hosts.data.IdentityRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class IdentitiesListViewModel @Inject constructor(
    private val repo: IdentityRepository,
) : ViewModel() {

    val identities: StateFlow<List<Identity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(identity: Identity) {
        viewModelScope.launch { repo.delete(identity) }
    }

    fun clone(identity: Identity) {
        viewModelScope.launch {
            val copy = Identity(
                id = UUID.randomUUID().toString(),
                userId = identity.userId,
                name = "${identity.name} (copia)",
                authType = identity.authType,
                encryptedSecret = identity.encryptedSecret.copyOf(),
                encryptedPassphrase = identity.encryptedPassphrase?.copyOf(),
                needsReentry = identity.needsReentry,
                createdAt = System.currentTimeMillis(),
            )
            repo.upsert(copy)
        }
    }

    fun clearReentryFlag(identity: Identity) {
        // Re-entry happens by editing the identity. This helper just exists so the
        // UI can offer an explicit "fix" affordance.
        viewModelScope.launch {
            repo.upsert(
                Identity(
                    id = identity.id,
                    userId = identity.userId,
                    name = identity.name,
                    authType = identity.authType,
                    encryptedSecret = identity.encryptedSecret,
                    encryptedPassphrase = identity.encryptedPassphrase,
                    needsReentry = false,
                    createdAt = identity.createdAt,
                )
            )
        }
    }
}
