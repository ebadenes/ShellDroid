package io.shelldroid.core.ssh

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

/**
 * Unit tests for [SshSessionManager] that don't need to actually open
 * sockets or mock the LibSshClient constructor. These cover the simple
 * state-query surface of the manager on an empty registry — enough to
 * catch regressions in the read-only paths.
 */
class SshSessionManagerTest {

    private fun newManager(): SshSessionManager =
        SshSessionManager(
            verifier = mockk(relaxed = true),
            serviceController = NoopSessionServiceController,
        )

    @Test
    fun `getClient returns null for unknown host`() {
        val mgr = newManager()
        assertThat(mgr.getClient("nope")).isNull()
    }

    @Test
    fun `activeCountFlow starts at zero`() {
        val mgr = newManager()
        assertThat(mgr.activeCountFlow.value).isEqualTo(0)
        assertThat(mgr.activeCount()).isEqualTo(0)
    }

    @Test
    fun `activeHostIds is empty on fresh manager`() {
        val mgr = newManager()
        assertThat(mgr.activeHostIds()).isEmpty()
    }

    @Test
    fun `disconnectAll is safe on empty manager`() {
        val mgr = newManager()
        mgr.disconnectAll()
        assertThat(mgr.activeCountFlow.value).isEqualTo(0)
        assertThat(mgr.activeHostIds()).isEmpty()
    }

    @Test
    fun `disconnect unknown host is a no-op`() {
        val mgr = newManager()
        mgr.disconnect("nope")
        assertThat(mgr.activeCountFlow.value).isEqualTo(0)
    }
}
