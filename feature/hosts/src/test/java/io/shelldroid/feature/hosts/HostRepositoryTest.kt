package io.shelldroid.feature.hosts

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.shelldroid.core.db.CurrentUserProvider
import io.shelldroid.core.db.dao.HostDao
import io.shelldroid.core.db.entities.Host
import io.shelldroid.core.db.entities.User
import io.shelldroid.feature.hosts.data.HostRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HostRepositoryTest {

    private val user = User(id = "u1", name = "me", createdAt = 0L)
    private fun host(id: String = "h1") = Host(
        id = id,
        userId = "u1",
        name = "Test",
        hostname = "example.com",
        port = 22,
        username = "root",
        identityId = null,
        createdAt = 0L,
    )

    @Test
    fun `observeAll flat-maps current user into DAO observeAll`() = runTest {
        val dao = mockk<HostDao>()
        val cur = mockk<CurrentUserProvider>()
        every { cur.observeCurrent() } returns flowOf(user)
        every { dao.observeAll("u1") } returns flowOf(listOf(host()))

        val repo = HostRepository(dao, cur)
        repo.observeAll().test {
            assertThat(awaitItem()).hasSize(1)
            awaitComplete()
        }
    }

    @Test
    fun `findById delegates to DAO`() = runTest {
        val dao = mockk<HostDao>()
        val cur = mockk<CurrentUserProvider>()
        coEvery { dao.findById("h1") } returns host()

        val repo = HostRepository(dao, cur)
        assertThat(repo.findById("h1")).isNotNull()
        coVerify { dao.findById("h1") }
    }

    @Test
    fun `upsert delegates to DAO`() = runTest {
        val dao = mockk<HostDao>(relaxed = true)
        val cur = mockk<CurrentUserProvider>()
        val h = host()

        HostRepository(dao, cur).upsert(h)
        coVerify { dao.upsert(h) }
    }

    @Test
    fun `delete delegates to DAO`() = runTest {
        val dao = mockk<HostDao>(relaxed = true)
        val cur = mockk<CurrentUserProvider>()
        val h = host()

        HostRepository(dao, cur).delete(h)
        coVerify { dao.delete(h) }
    }

    @Test
    fun `updateLastConnected forwards to DAO`() = runTest {
        val dao = mockk<HostDao>(relaxed = true)
        val cur = mockk<CurrentUserProvider>()

        HostRepository(dao, cur).updateLastConnected("h1", 12345L)
        coVerify { dao.updateLastConnectedAt("h1", 12345L) }
    }

    @Test
    fun `currentUserId resolves from provider`() = runTest {
        val dao = mockk<HostDao>()
        val cur = mockk<CurrentUserProvider>()
        coEvery { cur.current() } returns user

        assertThat(HostRepository(dao, cur).currentUserId()).isEqualTo("u1")
    }
}
