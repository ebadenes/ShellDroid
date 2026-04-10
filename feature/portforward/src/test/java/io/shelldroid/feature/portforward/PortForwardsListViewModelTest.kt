package io.shelldroid.feature.portforward

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.shelldroid.core.db.PortForwardType
import io.shelldroid.core.db.dao.HostDao
import io.shelldroid.core.db.entities.Host
import io.shelldroid.core.db.entities.PortForward
import io.shelldroid.core.ssh.PortForwardManager
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
class PortForwardsListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun pf(id: String = "p1", hostId: String = "h1") = PortForward(
        id = id,
        userId = "u1",
        hostId = hostId,
        type = PortForwardType.LOCAL,
        localPort = 8080,
        remoteHost = "127.0.0.1",
        remotePort = 80,
        autoStart = false,
        createdAt = 0L,
    )

    private fun host(id: String = "h1", name: String = "server") = Host(
        id = id,
        userId = "u1",
        name = name,
        hostname = "example.com",
        port = 22,
        username = "root",
        identityId = null,
        createdAt = 0L,
    )

    @Test
    fun `forwards flow exposes repository observable`() = runTest {
        val repo = mockk<PortForwardRepository>()
        val hostDao = mockk<HostDao>()
        every { repo.observeAll() } returns flowOf(listOf(pf()))
        coEvery { repo.currentUserId() } returns "u1"
        every { hostDao.observeAll("u1") } returns flowOf(listOf(host()))

        val vm = PortForwardsListViewModel(repo, hostDao, mockk<PortForwardManager>(relaxed = true))
        vm.forwards.test {
            assertThat(awaitItem()).isEmpty()
            assertThat(awaitItem()).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `grouped combines forwards with host lookup`() = runTest {
        val repo = mockk<PortForwardRepository>()
        val hostDao = mockk<HostDao>()
        every { repo.observeAll() } returns
            flowOf(listOf(pf("p1", "h1"), pf("p2", "h1"), pf("p3", "h2")))
        coEvery { repo.currentUserId() } returns "u1"
        every { hostDao.observeAll("u1") } returns
            flowOf(listOf(host("h1", "alpha"), host("h2", "beta")))

        val vm = PortForwardsListViewModel(repo, hostDao, mockk<PortForwardManager>(relaxed = true))
        vm.grouped.test {
            // initial empty then combined result
            assertThat(awaitItem()).isEmpty()
            val groups = awaitItem()
            assertThat(groups).hasSize(2)
            assertThat(groups.first().host?.name).isEqualTo("alpha")
            assertThat(groups.first().forwards).hasSize(2)
            assertThat(groups.last().host?.name).isEqualTo("beta")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete delegates to repository`() = runTest {
        val repo = mockk<PortForwardRepository>(relaxed = true)
        val hostDao = mockk<HostDao>()
        every { repo.observeAll() } returns flowOf(emptyList())
        coEvery { repo.currentUserId() } returns "u1"
        every { hostDao.observeAll("u1") } returns flowOf(emptyList())

        val vm = PortForwardsListViewModel(repo, hostDao, mockk<PortForwardManager>(relaxed = true))
        val p = pf()
        vm.delete(p)
        advanceUntilIdle()

        coVerify { repo.delete(p) }
    }
}
