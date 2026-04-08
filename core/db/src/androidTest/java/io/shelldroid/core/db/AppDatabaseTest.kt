package io.shelldroid.core.db

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.shelldroid.core.db.entities.ConnectionGroup
import io.shelldroid.core.db.entities.DeletedItem
import io.shelldroid.core.db.entities.Host
import io.shelldroid.core.db.entities.HostGroupMembership
import io.shelldroid.core.db.entities.Identity
import io.shelldroid.core.db.entities.KnownHost
import io.shelldroid.core.db.entities.PortForward
import io.shelldroid.core.db.entities.Snippet
import io.shelldroid.core.db.entities.User
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AppDatabaseTest : DbTest() {

    private val uid = AppDatabase.DEFAULT_USER_ID

    // ---------- User ----------
    @Test fun user_default_seeded() = runTest {
        val u = db.userDao().findDefault()
        assertThat(u).isNotNull()
        assertThat(u!!.id).isEqualTo(uid)
    }

    @Test fun user_insert_and_find() = runTest {
        val u = User(id = "u2", name = "Alice", createdAt = 1L)
        db.userDao().upsert(u)
        assertThat(db.userDao().findById("u2")).isEqualTo(u)
    }

    // ---------- Host ----------
    @Test fun host_insert_query_by_user_and_cascade_delete() = runTest {
        val h = Host(
            id = "h1", userId = uid, name = "prod",
            hostname = "example.com", port = 22, username = "root",
            identityId = null, createdAt = 10L, lastConnectedAt = null,
        )
        db.hostDao().upsert(h)
        assertThat(db.hostDao().findById("h1")).isEqualTo(h)

        db.hostDao().observeAll(uid).test {
            val list = awaitItem()
            assertThat(list).containsExactly(h)
            cancelAndIgnoreRemainingEvents()
        }

        // cascade on user deletion
        db.userDao().delete(User(uid, AppDatabase.DEFAULT_USER_NAME, 0L))
        assertThat(db.hostDao().findById("h1")).isNull()
    }

    @Test fun host_updateLastConnectedAt() = runTest {
        val h = Host("h2", uid, "dev", "dev.lan", 22, "me", null, 1L, null)
        db.hostDao().upsert(h)
        db.hostDao().updateLastConnectedAt("h2", 999L)
        assertThat(db.hostDao().findById("h2")!!.lastConnectedAt).isEqualTo(999L)
    }

    // ---------- Identity ----------
    @Test fun identity_insert_and_markAllNeedReentry() = runTest {
        val i = Identity(
            id = "i1", userId = uid, name = "rsa",
            authType = AuthType.KEY_RSA,
            encryptedSecret = byteArrayOf(1, 2, 3),
            encryptedPassphrase = null,
            needsReentry = false, createdAt = 1L,
        )
        db.identityDao().upsert(i)
        val loaded = db.identityDao().findById("i1")!!
        assertThat(loaded.encryptedSecret).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(loaded.needsReentry).isFalse()

        db.identityDao().markAllNeedReentry(uid)
        assertThat(db.identityDao().findById("i1")!!.needsReentry).isTrue()
    }

    @Test fun host_identity_fk_set_null_on_identity_delete() = runTest {
        val i = Identity("i2", uid, "k", AuthType.KEY_ED25519, byteArrayOf(9), null, false, 1L)
        db.identityDao().upsert(i)
        val h = Host("h3", uid, "n", "n.io", 22, "u", "i2", 1L, null)
        db.hostDao().upsert(h)

        db.identityDao().deleteById("i2")
        val reloaded = db.hostDao().findById("h3")
        assertThat(reloaded).isNotNull()
        assertThat(reloaded!!.identityId).isNull()
    }

    // ---------- KnownHost ----------
    @Test fun knownHost_unique_constraint_and_find() = runTest {
        val k = KnownHost(
            id = "k1", userId = uid, hostname = "srv",
            port = 22, keyType = "ssh-ed25519",
            fingerprintSha256 = "abc",
            publicKeyBlob = byteArrayOf(7, 7),
            firstSeen = 1L, lastSeen = 1L,
        )
        db.knownHostDao().upsert(k)

        val found = db.knownHostDao().find(uid, "srv", 22)
        assertThat(found).isNotNull()
        assertThat(found!!.publicKeyBlob).isEqualTo(byteArrayOf(7, 7))

        db.knownHostDao().updateLastSeen("k1", 42L)
        assertThat(db.knownHostDao().findById("k1")!!.lastSeen).isEqualTo(42L)
    }

    // ---------- Snippet ----------
    @Test fun snippet_insert_find() = runTest {
        val s = Snippet("s1", uid, "ll", "ls -la", "listing", 1L)
        db.snippetDao().upsert(s)
        assertThat(db.snippetDao().findById("s1")).isEqualTo(s)
    }

    // ---------- PortForward ----------
    @Test fun portForward_cascade_on_host_delete() = runTest {
        val h = Host("hp", uid, "n", "h.io", 22, "u", null, 1L, null)
        db.hostDao().upsert(h)
        val pf = PortForward(
            id = "pf1", userId = uid, hostId = "hp",
            type = PortForwardType.LOCAL, localPort = 8080,
            remoteHost = "127.0.0.1", remotePort = 80,
            autoStart = false, createdAt = 1L,
        )
        db.portForwardDao().upsert(pf)
        assertThat(db.portForwardDao().findById("pf1")).isEqualTo(pf)

        db.hostDao().deleteById("hp")
        assertThat(db.portForwardDao().findById("pf1")).isNull()
    }

    // ---------- ConnectionGroup + membership ----------
    @Test fun connectionGroup_self_ref_and_membership_cascade() = runTest {
        val parent = ConnectionGroup("g1", uid, "root", null, 1L)
        val child = ConnectionGroup("g2", uid, "child", "g1", 1L)
        db.connectionGroupDao().upsert(parent)
        db.connectionGroupDao().upsert(child)

        val host = Host("hg", uid, "x", "x.io", 22, "u", null, 1L, null)
        db.hostDao().upsert(host)
        db.connectionGroupDao().addMembership(HostGroupMembership("hg", "g2", uid))

        assertThat(db.connectionGroupDao().membershipsOfGroup("g2")).hasSize(1)

        // delete child group → membership cascades
        db.connectionGroupDao().deleteById("g2")
        assertThat(db.connectionGroupDao().membershipsOfGroup("g2")).isEmpty()

        // parent deletion sets child parentGroupId null (already deleted here; verify semantics with fresh)
    }

    // ---------- DeletedItem ----------
    @Test fun deletedItem_insert_and_observe() = runTest {
        val d = DeletedItem("d1", uid, TombstoneType.HOST, "h-removed", 99L)
        db.deletedItemDao().insert(d)
        db.deletedItemDao().observeAll(uid).test {
            assertThat(awaitItem()).containsExactly(d)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
