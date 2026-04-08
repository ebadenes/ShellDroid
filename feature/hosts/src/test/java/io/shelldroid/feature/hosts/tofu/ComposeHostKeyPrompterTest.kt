package io.shelldroid.feature.hosts.tofu

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ComposeHostKeyPrompterTest {

    @Test
    fun `promptFirstSeen suspends until response completed`() = runTest {
        val prompter = ComposeHostKeyPrompter()
        val deferredResult = async {
            prompter.promptFirstSeen("example.com", 22, "ssh-ed25519", "AAAA")
        }
        val emitted = prompter.prompts.first()
        assertThat(emitted.type).isEqualTo(PromptType.FIRST_SEEN)
        assertThat(emitted.hostname).isEqualTo("example.com")
        assertThat(emitted.fingerprint).isEqualTo("AAAA")
        emitted.response.complete(true)
        assertThat(deferredResult.await()).isTrue()
    }

    @Test
    fun `promptFirstSeen returns false when rejected`() = runTest {
        val prompter = ComposeHostKeyPrompter()
        val deferredResult = async {
            prompter.promptFirstSeen("h", 22, "k", "fp")
        }
        val emitted = prompter.prompts.first()
        emitted.response.complete(false)
        assertThat(deferredResult.await()).isFalse()
    }

    @Test
    fun `promptKeyChanged emits KEY_CHANGED prompt`() = runTest {
        val prompter = ComposeHostKeyPrompter()
        val job = async {
            prompter.promptKeyChanged("h", 22, "old", "new", "ssh-rsa")
        }
        val emitted = prompter.prompts.first()
        assertThat(emitted.type).isEqualTo(PromptType.KEY_CHANGED)
        assertThat(emitted.oldFingerprint).isEqualTo("old")
        assertThat(emitted.fingerprint).isEqualTo("new")
        emitted.response.complete(true)
        job.await()
    }
}
