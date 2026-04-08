package io.shelldroid.feature.hosts

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.shelldroid.core.db.AuthType
import io.shelldroid.core.db.entities.Host
import io.shelldroid.core.db.entities.Identity
import io.shelldroid.core.security.CredentialVault
import io.shelldroid.core.ssh.SshSessionManager
import io.shelldroid.feature.hosts.data.HostRepository
import io.shelldroid.feature.hosts.data.IdentityRepository
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
class HostsListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun host(id: String = "h1", identityId: String? = "i1") = Host(
        id = id,
        userId = "u1",
        name = "Test",
        hostname = "example.com",
        port = 22,
        username = "root",
        identityId = identityId,
        createdAt = 0L,
    )

    private fun identity(needsReentry: Boolean = false) = Identity(
        id = "i1",
        userId = "u1",
        name = "id",
        authType = AuthType.PASSWORD,
        encryptedSecret = ByteArray(4),
        encryptedPassphrase = null,
        needsReentry = needsReentry,
        createdAt = 0L,
    )

    @Test
    fun `hosts flow exposes repository observable`() = runTest {
        val repo = mockk<HostRepository>()
        val identityRepo = mockk<IdentityRepository>()
        val mgr = mockk<SshSessionManager>()
        val vault = mockk<CredentialVault>()
        every { repo.observeAll() } returns flowOf(listOf(host()))

        val vm = HostsListViewModel(repo, identityRepo, mgr, vault)
        vm.hosts.test {
            assertThat(awaitItem()).isEmpty() // initial
            assertThat(awaitItem()).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `connect with needsReentry returns Error`() = runTest {
        val repo = mockk<HostRepository>()
        val identityRepo = mockk<IdentityRepository>()
        val mgr = mockk<SshSessionManager>()
        val vault = mockk<CredentialVault>()
        every { repo.observeAll() } returns flowOf(emptyList())
        every { mgr.getClient(any()) } returns null
        coEvery { repo.findById("h1") } returns host()
        coEvery { identityRepo.findById("i1") } returns identity(needsReentry = true)

        val vm = HostsListViewModel(repo, identityRepo, mgr, vault)
        vm.connect("h1")
        advanceUntilIdle()

        val state = vm.connectState.value
        assertThat(state).isInstanceOf(HostsListViewModel.ConnectState.Error::class.java)
        assertThat((state as HostsListViewModel.ConnectState.Error).message).contains("invalidada")
    }

    @Test
    fun `connect with missing identity returns Error`() = runTest {
        val repo = mockk<HostRepository>()
        val identityRepo = mockk<IdentityRepository>()
        val mgr = mockk<SshSessionManager>()
        val vault = mockk<CredentialVault>()
        every { repo.observeAll() } returns flowOf(emptyList())
        every { mgr.getClient(any()) } returns null
        coEvery { repo.findById("h1") } returns host(identityId = null)

        val vm = HostsListViewModel(repo, identityRepo, mgr, vault)
        vm.connect("h1")
        advanceUntilIdle()

        val state = vm.connectState.value
        assertThat(state).isInstanceOf(HostsListViewModel.ConnectState.Error::class.java)
    }

    @Test
    fun `connect missing host returns Error`() = runTest {
        val repo = mockk<HostRepository>()
        val identityRepo = mockk<IdentityRepository>()
        val mgr = mockk<SshSessionManager>()
        val vault = mockk<CredentialVault>()
        every { repo.observeAll() } returns flowOf(emptyList())
        every { mgr.getClient(any()) } returns null
        coEvery { repo.findById("nope") } returns null

        val vm = HostsListViewModel(repo, identityRepo, mgr, vault)
        vm.connect("nope")
        advanceUntilIdle()

        assertThat(vm.connectState.value).isInstanceOf(HostsListViewModel.ConnectState.Error::class.java)
    }

    @Test
    fun `delete delegates to repository`() = runTest {
        val repo = mockk<HostRepository>(relaxed = true)
        val identityRepo = mockk<IdentityRepository>()
        val mgr = mockk<SshSessionManager>()
        val vault = mockk<CredentialVault>()
        every { repo.observeAll() } returns flowOf(emptyList())
        every { mgr.getClient(any()) } returns null

        val h = host()
        val vm = HostsListViewModel(repo, identityRepo, mgr, vault)
        vm.delete(h)
        advanceUntilIdle()

        coVerify { repo.delete(h) }
    }
}
