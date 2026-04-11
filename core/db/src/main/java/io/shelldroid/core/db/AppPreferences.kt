package io.shelldroid.core.db

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persisted UI preferences backed by [UserPrefsDataStore].
 *
 * Distinct from [CurrentUserProvider]: that handles the active-user
 * selection, this one stores plain UI toggles (keep screen on, etc.)
 * that don't need their own DAO.
 */
@Singleton
class AppPreferences @Inject constructor(
    @UserPrefsDataStore private val dataStore: DataStore<Preferences>,
) {
    val keepScreenOnFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEEP_SCREEN_ON_KEY] ?: false
    }

    suspend fun setKeepScreenOn(on: Boolean) {
        dataStore.edit { it[KEEP_SCREEN_ON_KEY] = on }
    }

    companion object {
        private val KEEP_SCREEN_ON_KEY = booleanPreferencesKey("keep_screen_on")
    }
}
