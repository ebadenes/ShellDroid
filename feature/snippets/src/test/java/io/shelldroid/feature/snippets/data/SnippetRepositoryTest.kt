package io.shelldroid.feature.snippets.data

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.shelldroid.core.db.AppDatabase
import io.shelldroid.core.db.CurrentUserProvider
import io.shelldroid.core.db.dao.SnippetDao
import io.shelldroid.core.db.entities.Snippet
import io.shelldroid.core.db.entities.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SnippetRepositoryTest {

    private val user = User(id = AppDatabase.DEFAULT_USER_ID, name = "Default", createdAt = 0L)

    private fun snippet(id: String = "s1") = Snippet(
        id = id,
        userId = user.id,
        name = "n",
        command = "c",
        description = null,
        createdAt = 0L,
    )

    @Test
    fun `observeAll flat-maps currentUser into dao observable`() = runTest {
        val dao = mockk<SnippetDao>()
        val cu = mockk<CurrentUserProvider>()
        every { cu.observeCurrent() } returns flowOf(user)
        every { dao.observeAll(user.id) } returns flowOf(listOf(snippet()))

        val repo = SnippetRepository(dao, cu)
        val result = repo.observeAll().first()
        assertThat(result).hasSize(1)
    }

    @Test
    fun `findById delegates to dao`() = runTest {
        val dao = mockk<SnippetDao>()
        val cu = mockk<CurrentUserProvider>()
        coEvery { dao.findById("s1") } returns snippet()

        val repo = SnippetRepository(dao, cu)
        assertThat(repo.findById("s1")).isNotNull()
    }

    @Test
    fun `upsert delegates to dao`() = runTest {
        val dao = mockk<SnippetDao>(relaxed = true)
        val cu = mockk<CurrentUserProvider>()
        val s = snippet()

        SnippetRepository(dao, cu).upsert(s)
        coVerify { dao.upsert(s) }
    }

    @Test
    fun `delete delegates to dao`() = runTest {
        val dao = mockk<SnippetDao>(relaxed = true)
        val cu = mockk<CurrentUserProvider>()
        val s = snippet()

        SnippetRepository(dao, cu).delete(s)
        coVerify { dao.delete(s) }
    }

    @Test
    fun `currentUserId resolves via provider`() = runTest {
        val dao = mockk<SnippetDao>()
        val cu = mockk<CurrentUserProvider>()
        coEvery { cu.current() } returns user

        val repo = SnippetRepository(dao, cu)
        assertThat(repo.currentUserId()).isEqualTo(user.id)
    }
}
