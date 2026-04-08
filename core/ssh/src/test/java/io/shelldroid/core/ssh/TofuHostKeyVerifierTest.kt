package io.shelldroid.core.ssh

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.shelldroid.core.db.CurrentUserProvider
import io.shelldroid.core.db.dao.KnownHostDao
import io.shelldroid.core.db.entities.KnownHost
import io.shelldroid.core.db.entities.User
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TofuHostKeyVerifierTest {

    private val dao = mockk<KnownHostDao>(relaxed = true)
    private val prompter = mockk<HostKeyPrompter>(relaxed = true)
    private val users = mockk<CurrentUserProvider>()
    private val verifier = TofuHostKeyVerifier(dao, prompter, users)

    private val user = User(id = "u1", name = "test", createdAt = 0L)

    private val blob = byteArrayOf(1, 2, 3, 4, 5)
    private val expectedFp = TofuHostKeyVerifier.sha256Base64(blob)

    init {
        coEvery { users.current() } returns user
    }

    @Test fun `first seen accepted persists known host`() = runTest {
        coEvery { dao.find("u1", "h", 22) } returns null
        coEvery { prompter.promptFirstSeen(any(), any(), any(), any()) } returns true

        val ok = verifier.verify("h", 22, "ssh-ed25519", blob)

        assertThat(ok).isTrue()
        coVerify {
            dao.upsert(match<KnownHost> {
                it.hostname == "h" && it.port == 22 && it.fingerprintSha256 == expectedFp
            })
        }
    }

    @Test fun `first seen rejected does not persist`() = runTest {
        coEvery { dao.find("u1", "h", 22) } returns null
        coEvery { prompter.promptFirstSeen(any(), any(), any(), any()) } returns false

        val ok = verifier.verify("h", 22, "ssh-ed25519", blob)

        assertThat(ok).isFalse()
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test fun `match updates lastSeen and accepts`() = runTest {
        val existing = KnownHost(
            id = "k1", userId = "u1", hostname = "h", port = 22,
            keyType = "ssh-ed25519", fingerprintSha256 = expectedFp,
            publicKeyBlob = blob, firstSeen = 1L, lastSeen = 1L,
        )
        coEvery { dao.find("u1", "h", 22) } returns existing

        val ok = verifier.verify("h", 22, "ssh-ed25519", blob)

        assertThat(ok).isTrue()
        coVerify { dao.updateLastSeen(eq("k1"), any()) }
        coVerify(exactly = 0) { prompter.promptFirstSeen(any(), any(), any(), any()) }
    }

    @Test fun `mismatch always rejects and notifies`() = runTest {
        val existing = KnownHost(
            id = "k1", userId = "u1", hostname = "h", port = 22,
            keyType = "ssh-ed25519", fingerprintSha256 = "OLD_FP",
            publicKeyBlob = byteArrayOf(9, 9, 9), firstSeen = 1L, lastSeen = 1L,
        )
        coEvery { dao.find("u1", "h", 22) } returns existing

        val ok = verifier.verify("h", 22, "ssh-ed25519", blob)

        assertThat(ok).isFalse()
        coVerify {
            prompter.promptKeyChanged("h", 22, "OLD_FP", expectedFp, "ssh-ed25519")
        }
        coVerify(exactly = 0) { dao.upsert(any()) }
        coVerify(exactly = 0) { dao.updateLastSeen(any(), any()) }
    }

    @Test fun `sha256Base64 is deterministic and unpadded`() {
        // SHA-256("") = 47DEQpj8HBSa-_TImW-5JCeuQeRkm5NMpJWZG3hSuFU= (padded)
        // unpadded:    47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU
        val empty = TofuHostKeyVerifier.sha256Base64(ByteArray(0))
        assertThat(empty).isEqualTo("47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU")
        assertThat(empty).doesNotContain("=")
    }
}
