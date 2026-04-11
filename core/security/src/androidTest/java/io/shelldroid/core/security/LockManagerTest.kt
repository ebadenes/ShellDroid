package io.shelldroid.core.security

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LockManagerTest {

    private lateinit var lockManager: LockManager

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vault = CredentialVault(ctx)
        val file = File(ctx.cacheDir, "lock-test-${UUID.randomUUID()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        lockManager = LockManager(vault, dataStore, ctx)
    }

    @Test
    fun lock_disabled_by_default() = runBlocking {
        assertThat(lockManager.isLockEnabled()).isFalse()
        assertThat(lockManager.isLocked()).isFalse()
    }

    @Test
    fun setLockEnabled_true_then_false() = runBlocking {
        lockManager.setLockEnabled(true)
        assertThat(lockManager.isLockEnabled()).isTrue()
        lockManager.setLockEnabled(false)
        assertThat(lockManager.isLockEnabled()).isFalse()
    }

    @Test
    fun isLocked_true_when_enabled_and_no_unlock_yet() = runBlocking {
        lockManager.setLockEnabled(true)
        assertThat(lockManager.isLocked()).isTrue()
    }

    @Test
    fun isLocked_false_after_markUnlocked_within_window() = runBlocking {
        lockManager.setLockEnabled(true)
        lockManager.setAutoLockMode(AutoLockMode.FIVE_MIN)
        lockManager.markUnlocked()
        assertThat(lockManager.isLocked()).isFalse()
    }

    @Test
    fun autoLockMode_persists() = runBlocking {
        lockManager.setAutoLockMode(AutoLockMode.FIFTEEN_MIN)
        assertThat(lockManager.getAutoLockMode()).isEqualTo(AutoLockMode.FIFTEEN_MIN)
    }
}
