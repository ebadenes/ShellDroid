package io.shelldroid.feature.identities

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.shelldroid.core.db.AuthType
import io.shelldroid.core.db.entities.Identity
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
class IdentitiesListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

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
    fun `identities flow exposes repository observable`() = runTest {
        val repo = mockk<IdentityRepository>()
        every { repo.observeAll() } returns flowOf(listOf(identity()))

        val vm = IdentitiesListViewModel(repo)
        vm.identities.test {
            assertThat(awaitItem()).isEmpty() // initial emission from stateIn
            assertThat(awaitItem()).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete delegates to repository`() = runTest {
        val repo = mockk<IdentityRepository>(relaxed = true)
        every { repo.observeAll() } returns flowOf(emptyList())

        val vm = IdentitiesListViewModel(repo)
        val id = identity()
        vm.delete(id)
        advanceUntilIdle()

        coVerify { repo.delete(id) }
    }

    @Test
    fun `clearReentryFlag upserts with needsReentry false`() = runTest {
        val repo = mockk<IdentityRepository>(relaxed = true)
        every { repo.observeAll() } returns flowOf(emptyList())

        val vm = IdentitiesListViewModel(repo)
        vm.clearReentryFlag(identity(needsReentry = true))
        advanceUntilIdle()

        coVerify {
            repo.upsert(match { it.id == "i1" && !it.needsReentry })
        }
    }
}
