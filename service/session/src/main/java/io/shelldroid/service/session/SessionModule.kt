package io.shelldroid.service.session

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.shelldroid.core.ssh.SessionServiceController
import javax.inject.Singleton

/**
 * Hilt bindings for `:service:session`. Binds [SessionServiceControllerImpl]
 * as the [SessionServiceController] consumed by `SshSessionManager` in
 * `:core:ssh`, completing the inverted dependency.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {
    @Binds
    @Singleton
    abstract fun bindSessionServiceController(
        impl: SessionServiceControllerImpl,
    ): SessionServiceController
}
