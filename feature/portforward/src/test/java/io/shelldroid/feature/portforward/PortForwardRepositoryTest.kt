package io.shelldroid.feature.portforward

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.shelldroid.core.db.CurrentUserProvider
import io.shelldroid.core.db.PortForwardType
import io.shelldroid.core.db.dao.PortForwardDao
import io.shelldroid.core.db.entities.PortForward
import io.shelldroid.core.db.entities.User
import io.shelldroid.feature.portforward.data.PortForwardRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PortForwardRepositoryTest {

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

    private fun user(id: String = "u1") = User(
        id = id,
        name = "me",
        createdAt = 0L,
    )

    @Test
    fun `observeAll scopes by current user`() = runTest {
        val dao = mockk<PortForwardDao>()
        val cu = mockk<CurrentUserProvider>()
        every { cu.observeCurrent() } returns flowOf(user())
        every { dao.observeAll("u1") } returns flowOf(listOf(pf()))

        val repo = PortForwardRepository(dao, cu)
        val result = repo.observeAll().first()
        assertThat(result).hasSize(1)
        assertThat(result.first().id).isEqualTo("p1")
    }

    @Test
    fun `save delegates to dao upsert`() = runTest {
        val dao = mockk<PortForwardDao>(relaxed = true)
        val cu = mockk<CurrentUserProvider>()
        val repo = PortForwardRepository(dao, cu)

        val p = pf()
        repo.save(p)
        coVerify { dao.upsert(p) }
    }

    @Test
    fun `delete delegates to dao delete`() = runTest {
        val dao = mockk<PortForwardDao>(relaxed = true)
        val cu = mockk<CurrentUserProvider>()
        val repo = PortForwardRepository(dao, cu)

        val p = pf()
        repo.delete(p)
        coVerify { dao.delete(p) }
    }
}
