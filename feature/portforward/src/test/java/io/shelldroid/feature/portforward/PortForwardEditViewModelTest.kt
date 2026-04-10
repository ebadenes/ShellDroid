package io.shelldroid.feature.portforward

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.shelldroid.core.db.PortForwardType
import io.shelldroid.core.db.dao.HostDao
import io.shelldroid.core.db.entities.Host
import io.shelldroid.core.db.entities.PortForward
import io.shelldroid.feature.portforward.data.PortForwardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PortForwardEditViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun existing() = PortForward(
        id = "p1",
        userId = "u1",
        hostId = "h1",
        type = PortForwardType.REMOTE,
        localPort = 9000,
        remoteHost = "10.0.0.1",
        remotePort = 22,
        autoStart = false,
        createdAt = 0L,
    )

    private fun buildVm(
        repo: PortForwardRepository,
        hostDao: HostDao = mockk<HostDao>().apply {
            every { observeAll(any()) } returns flowOf(
                listOf(
                    Host(id = "h1", userId = "u1", name = "server", hostname = "example.com", port = 22, username = "root", identityId = null, createdAt = 0L),
                ),
            )
        },
        savedState: SavedStateHandle = SavedStateHandle(),
    ): PortForwardEditViewModel {
        coEvery { repo.currentUserId() } returns "u1"
        return PortForwardEditViewModel(repo, hostDao, savedState)
    }

    @Test
    fun `load populates form from existing port forward`() = runTest {
        val repo = mockk<PortForwardRepository>(relaxed = true)
        coEvery { repo.findById("p1") } returns existing()

        val vm = buildVm(repo)
        vm.load("p1")
        advanceUntilIdle()

        val s = vm.form.value
        assertThat(s.id).isEqualTo("p1")
        assertThat(s.hostId).isEqualTo("h1")
        assertThat(s.type).isEqualTo(PortForwardType.REMOTE)
        assertThat(s.sourcePort).isEqualTo("9000")
        assertThat(s.destHost).isEqualTo("10.0.0.1")
        assertThat(s.destPort).isEqualTo("22")
    }

    @Test
    fun `source port validation rejects out of range`() = runTest {
        val repo = mockk<PortForwardRepository>(relaxed = true)
        val vm = buildVm(repo)

        vm.onHost("h1")
        vm.onSourcePort("70000")
        vm.onDestHost("host")
        vm.onDestPort("80")
        advanceUntilIdle()

        assertThat(vm.form.value.sourcePortError).isNotNull()
        assertThat(vm.form.value.isValid).isFalse()
    }

    @Test
    fun `dest port validation required when not dynamic`() = runTest {
        val repo = mockk<PortForwardRepository>(relaxed = true)
        val vm = buildVm(repo)

        vm.onHost("h1")
        vm.onType(PortForwardType.LOCAL)
        vm.onSourcePort("8080")
        vm.onDestHost("host")
        // no destPort
        advanceUntilIdle()

        assertThat(vm.form.value.destPortError).isNotNull()
        assertThat(vm.form.value.isValid).isFalse()
    }

    @Test
    fun `dynamic type ignores dest host and dest port errors`() = runTest {
        val repo = mockk<PortForwardRepository>(relaxed = true)
        val vm = buildVm(repo)

        vm.onHost("h1")
        vm.onType(PortForwardType.DYNAMIC)
        vm.onSourcePort("1080")
        advanceUntilIdle()

        val s = vm.form.value
        assertThat(s.destHostError).isNull()
        assertThat(s.destPortError).isNull()
        assertThat(s.isValid).isTrue()
    }

    @Test
    fun `save persists a valid local forward`() = runTest {
        val repo = mockk<PortForwardRepository>(relaxed = true)
        val slot = slot<PortForward>()
        coEvery { repo.save(capture(slot)) } returns Unit

        val vm = buildVm(repo)
        vm.onHost("h1")
        vm.onType(PortForwardType.LOCAL)
        vm.onSourcePort("8080")
        vm.onDestHost("10.0.0.2")
        vm.onDestPort("80")
        vm.save()
        advanceUntilIdle()

        coVerify { repo.save(any()) }
        assertThat(slot.captured.hostId).isEqualTo("h1")
        assertThat(slot.captured.localPort).isEqualTo(8080)
        assertThat(slot.captured.remoteHost).isEqualTo("10.0.0.2")
        assertThat(slot.captured.remotePort).isEqualTo(80)
        assertThat(vm.form.value.saved).isTrue()
    }
}
