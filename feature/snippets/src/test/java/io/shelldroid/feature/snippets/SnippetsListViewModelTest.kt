package io.shelldroid.feature.snippets

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.shelldroid.core.db.entities.Snippet
import io.shelldroid.feature.snippets.data.SnippetRepository
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
class SnippetsListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun snippet(id: String = "s1") = Snippet(
        id = id,
        userId = "u1",
        name = "list files",
        command = "ls -la",
        description = null,
        createdAt = 0L,
    )

    @Test
    fun `snippets flow exposes repository data`() = runTest {
        val repo = mockk<SnippetRepository>()
        every { repo.observeAll() } returns flowOf(listOf(snippet()))

        val vm = SnippetsListViewModel(repo)
        vm.snippets.test {
            assertThat(awaitItem()).isEmpty() // initial
            assertThat(awaitItem()).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty list initial state`() = runTest {
        val repo = mockk<SnippetRepository>()
        every { repo.observeAll() } returns flowOf(emptyList())

        val vm = SnippetsListViewModel(repo)
        assertThat(vm.snippets.value).isEmpty()
    }

    @Test
    fun `delete delegates to repository`() = runTest {
        val repo = mockk<SnippetRepository>(relaxed = true)
        every { repo.observeAll() } returns flowOf(emptyList())

        val s = snippet()
        val vm = SnippetsListViewModel(repo)
        vm.delete(s)
        advanceUntilIdle()

        coVerify { repo.delete(s) }
    }
}
