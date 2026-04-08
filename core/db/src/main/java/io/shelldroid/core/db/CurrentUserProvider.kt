package io.shelldroid.core.db

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.shelldroid.core.db.dao.UserDao
import io.shelldroid.core.db.entities.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the currently-active user.
 *
 * MVP: always resolves to [AppDatabase.DEFAULT_USER_ID]. Multi-user switching
 * API is wired to DataStore so post-MVP adoption is transparent.
 */
@Singleton
class CurrentUserProvider @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val userDao: UserDao,
) {
    suspend fun current(): User {
        val id = dataStore.data
            .map { it[CURRENT_USER_KEY] ?: AppDatabase.DEFAULT_USER_ID }
            .first()
        return userDao.findById(id)
            ?: userDao.findDefault()
            ?: error("Default user missing — database not initialised correctly")
    }

    fun observeCurrent(): Flow<User> =
        dataStore.data.map { prefs ->
            val id = prefs[CURRENT_USER_KEY] ?: AppDatabase.DEFAULT_USER_ID
            userDao.findById(id)
                ?: userDao.findDefault()
                ?: error("Default user missing — database not initialised correctly")
        }

    suspend fun switchTo(userId: String) {
        dataStore.edit { it[CURRENT_USER_KEY] = userId }
    }

    companion object {
        private val CURRENT_USER_KEY = stringPreferencesKey("current_user_id")
    }
}
