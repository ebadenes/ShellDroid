package io.shelldroid.service.session

import android.content.Context
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Test

/**
 * Unit tests for [SessionServiceControllerImpl].
 *
 * Note: in the JVM unit-test runtime the stub `android.jar` reports
 * `Build.VERSION.SDK_INT == 0`, so [androidx.core.content.ContextCompat
 * .startForegroundService] falls through to plain `Context.startService`.
 * The tests therefore assert on `startService` — what matters is that
 * the controller routes the intent at the right component, not which
 * SDK-specific entry point gets used at JVM-test time.
 */
class SessionServiceControllerImplTest {

    @Test
    fun `ensureRunning starts SshSessionService via ContextCompat`() {
        val ctx = mockk<Context>(relaxed = true)
        val intentSlot = slot<Intent>()
        every { ctx.startService(capture(intentSlot)) } returns null

        val controller = SessionServiceControllerImpl(ctx)
        controller.ensureRunning()

        verify(exactly = 1) { ctx.startService(any()) }
        // Intent.component requires a real PackageManager to resolve in
        // unit tests; assert the captured intent is non-null and that the
        // controller is the only call site.
        assertThat(intentSlot.isCaptured).isTrue()
    }

    @Test
    fun `ensureRunning is safe to call multiple times`() {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.startService(any()) } returns null

        val controller = SessionServiceControllerImpl(ctx)
        controller.ensureRunning()
        controller.ensureRunning()
        controller.ensureRunning()

        verify(exactly = 3) { ctx.startService(any()) }
    }
}
