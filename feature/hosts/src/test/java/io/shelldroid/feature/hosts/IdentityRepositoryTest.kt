package io.shelldroid.feature.hosts

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.shelldroid.core.db.AuthType
import io.shelldroid.core.db.CurrentUserProvider
import io.shelldroid.core.db.dao.IdentityDao
import io.shelldroid.core.db.entities.Identity
import io.shelldroid.core.db.entities.User
import io.shelldroid.feature.hosts.data.IdentityRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class IdentityRepositoryTest {

    private val user = User(id = "u1", name = "me", createdAt = 0L)
    private fun identity(id: String = "i1") = Identity(
        id = id,
        userId = "u1",
        name = "id",
        authType = AuthType.PASSWORD,
        encryptedSecret = ByteArray(4),
        encryptedPassphrase = null,
        needsReentry = false,
        createdAt = 0L,
    )

    @Test
    fun `observeAll flat-maps current user into DAO observeAll`() = runTest {
        val dao = mockk<IdentityDao>()
        val cur = mockk<CurrentUserProvider>()
        every { cur.observeCurrent() } returns flowOf(user)
        every { dao.observeAll("u1") } returns flowOf(listOf(identity()))

        val repo = IdentityRepository(dao, cur)
        repo.observeAll().test {
            assertThat(awaitItem()).hasSize(1)
            awaitComplete()
        }
    }

    @Test
    fun `findById delegates to DAO`() = runTest {
        val dao = mockk<IdentityDao>()
        val cur = mockk<CurrentUserProvider>()
        coEvery { dao.findById("i1") } returns identity()

        assertThat(IdentityRepository(dao, cur).findById("i1")).isNotNull()
        coVerify { dao.findById("i1") }
    }

    @Test
    fun `upsert delegates to DAO`() = runTest {
        val dao = mockk<IdentityDao>(relaxed = true)
        val cur = mockk<CurrentUserProvider>()
        val id = identity()

        IdentityRepository(dao, cur).upsert(id)
        coVerify { dao.upsert(id) }
    }

    @Test
    fun `delete delegates to DAO`() = runTest {
        val dao = mockk<IdentityDao>(relaxed = true)
        val cur = mockk<CurrentUserProvider>()
        val id = identity()

        IdentityRepository(dao, cur).delete(id)
        coVerify { dao.delete(id) }
    }

    @Test
    fun `currentUserId resolves from provider`() = runTest {
        val dao = mockk<IdentityDao>()
        val cur = mockk<CurrentUserProvider>()
        coEvery { cur.current() } returns user

        assertThat(IdentityRepository(dao, cur).currentUserId()).isEqualTo("u1")
    }
}
