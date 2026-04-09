package io.shelldroid.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.shelldroid.core.db.CurrentUserProvider
import io.shelldroid.core.db.dao.KnownHostDao
import io.shelldroid.core.db.entities.KnownHost
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class KnownHostsViewModel @Inject constructor(
    private val knownHostDao: KnownHostDao,
    private val currentUserProvider: CurrentUserProvider,
) : ViewModel() {

    val knownHosts: StateFlow<List<KnownHost>> =
        flow { emit(currentUserProvider.current().id) }
            .flatMapLatest { userId -> knownHostDao.observeAll(userId) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(knownHost: KnownHost) {
        viewModelScope.launch {
            knownHostDao.delete(knownHost)
        }
    }
}
