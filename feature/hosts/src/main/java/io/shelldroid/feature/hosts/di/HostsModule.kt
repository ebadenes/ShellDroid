package io.shelldroid.feature.hosts.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.shelldroid.core.ssh.HostKeyPrompter
import io.shelldroid.feature.hosts.tofu.ComposeHostKeyPrompter

@Module
@InstallIn(SingletonComponent::class)
abstract class HostsModule {
    @Binds
    abstract fun bindHostKeyPrompter(impl: ComposeHostKeyPrompter): HostKeyPrompter
}
