package io.shelldroid.feature.snippets

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.shelldroid.core.db.entities.Snippet
import io.shelldroid.feature.snippets.data.SnippetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SnippetEditViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun snippet(id: String = "s1") = Snippet(
        id = id,
        userId = "u1",
        name = "list",
        command = "ls -la",
        description = null,
        createdAt = 42L,
    )

    @Test
    fun `loads existing snippet on init if id is provided`() = runTest {
        val repo = mockk<SnippetRepository>()
        coEvery { repo.findById("s1") } returns snippet()

        val vm = SnippetEditViewModel(repo)
        vm.load("s1")
        advanceUntilIdle()

        val f = vm.form.value
        assertThat(f.id).isEqualTo("s1")
        assertThat(f.name).isEqualTo("list")
        assertThat(f.command).isEqualTo("ls -la")
    }

    @Test
    fun `updateName and updateCommand mutate form state`() = runTest {
        val repo = mockk<SnippetRepository>()
        val vm = SnippetEditViewModel(repo)

        vm.onName("deploy")
        vm.onCommand("./deploy.sh prod")

        assertThat(vm.form.value.name).isEqualTo("deploy")
        assertThat(vm.form.value.command).isEqualTo("./deploy.sh prod")
        assertThat(vm.form.value.isValid).isTrue()
    }

    @Test
    fun `save calls repository and marks saved`() = runTest {
        val repo = mockk<SnippetRepository>(relaxed = true)
        coEvery { repo.currentUserId() } returns "u1"

        val vm = SnippetEditViewModel(repo)
        vm.onName("deploy")
        vm.onCommand("./deploy.sh")
        vm.save()
        advanceUntilIdle()

        coVerify { repo.upsert(any()) }
        assertThat(vm.form.value.saved).isTrue()
        assertThat(vm.form.value.saving).isFalse()
    }

    @Test
    fun `save with invalid form sets error`() = runTest {
        val repo = mockk<SnippetRepository>(relaxed = true)

        val vm = SnippetEditViewModel(repo)
        vm.save()
        advanceUntilIdle()

        assertThat(vm.form.value.error).isNotNull()
        assertThat(vm.form.value.saved).isFalse()
    }
}
