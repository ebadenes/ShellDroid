package io.shelldroid.feature.hosts.tofu

import io.shelldroid.core.ssh.HostKeyPrompter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class PromptType { FIRST_SEEN, KEY_CHANGED }

data class HostKeyPrompt(
    val type: PromptType,
    val hostname: String,
    val port: Int,
    val keyType: String,
    val fingerprint: String,
    val oldFingerprint: String? = null,
    val response: CompletableDeferred<Boolean>,
)

/**
 * UI-bridging implementation of [HostKeyPrompter]. The verifier (background)
 * suspends in `prompt*` while a Compose layer collects [prompts] and resolves
 * each [HostKeyPrompt.response] from a dialog.
 */
@Singleton
class ComposeHostKeyPrompter @Inject constructor() : HostKeyPrompter {

    private val _prompts = MutableSharedFlow<HostKeyPrompt>(
        replay = 0,
        extraBufferCapacity = 4,
    )
    val prompts: SharedFlow<HostKeyPrompt> = _prompts.asSharedFlow()

    override suspend fun promptFirstSeen(
        hostname: String,
        port: Int,
        keyType: String,
        fingerprint: String,
    ): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        _prompts.emit(
            HostKeyPrompt(
                type = PromptType.FIRST_SEEN,
                hostname = hostname,
                port = port,
                keyType = keyType,
                fingerprint = fingerprint,
                response = deferred,
            )
        )
        return deferred.await()
    }

    override suspend fun promptKeyChanged(
        hostname: String,
        port: Int,
        oldFingerprint: String,
        newFingerprint: String,
        newKeyType: String,
    ) {
        val deferred = CompletableDeferred<Boolean>()
        _prompts.emit(
            HostKeyPrompt(
                type = PromptType.KEY_CHANGED,
                hostname = hostname,
                port = port,
                keyType = newKeyType,
                fingerprint = newFingerprint,
                oldFingerprint = oldFingerprint,
                response = deferred,
            )
        )
        // Caller doesn't read the boolean, but we wait for ack so the user sees the warning.
        deferred.await()
    }
}
