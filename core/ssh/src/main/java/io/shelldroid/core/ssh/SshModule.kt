package io.shelldroid.core.ssh

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for `:core:ssh`.
 *
 * [SshSessionManager] and [TofuHostKeyVerifier] are constructor-injected
 * `@Singleton`s, so no explicit `@Provides` is needed here. This module
 * exists as the install point and as a place to add bindings later
 * (e.g. test fakes via `@TestInstallIn`).
 *
 * NOTE: [HostKeyPrompter] has no binding in this module on purpose — it
 * lives in feature/UI modules. Apps that depend on `:core:ssh` MUST
 * provide a `HostKeyPrompter` somewhere in their Hilt graph (typically
 * `:feature:hosts`) before injecting [TofuHostKeyVerifier].
 */
@Module
@InstallIn(SingletonComponent::class)
object SshModule
