package io.shelldroid.core.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for `:core:security`.
 *
 * Provides a dedicated `DataStore<Preferences>` ("security.preferences_pb") so
 * lock/PIN state is isolated from other modules' DataStore files. [CredentialVault],
 * [LockManager] and [AutoLockObserver] are constructor-injected and require no
 * explicit @Provides.
 */
private val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "security"
)

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    @SecurityDataStore
    fun provideSecurityDataStore(@ApplicationContext ctx: Context): DataStore<Preferences> =
        ctx.securityDataStore
}
