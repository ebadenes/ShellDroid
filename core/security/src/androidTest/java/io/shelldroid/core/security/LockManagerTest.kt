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
        lockManager = LockManager(vault, dataStore)
    }

    @Test
    fun setPin_then_verify_correct() = runBlocking {
        lockManager.setPin("1234".toCharArray())
        assertThat(lockManager.hasPin()).isTrue()
        assertThat(lockManager.verifyPin("1234".toCharArray())).isTrue()
    }

    @Test
    fun verify_wrong_pin_fails() = runBlocking {
        lockManager.setPin("1234".toCharArray())
        assertThat(lockManager.verifyPin("0000".toCharArray())).isFalse()
    }

    @Test
    fun verify_without_pin_returns_false() = runBlocking {
        assertThat(lockManager.hasPin()).isFalse()
        assertThat(lockManager.verifyPin("1234".toCharArray())).isFalse()
    }

    @Test
    fun clearPin_removes_state() = runBlocking {
        lockManager.setPin("1234".toCharArray())
        lockManager.clearPin()
        assertThat(lockManager.hasPin()).isFalse()
    }

    @Test
    fun isLocked_true_when_no_unlock_yet() = runBlocking {
        lockManager.setPin("1234".toCharArray())
        assertThat(lockManager.isLocked()).isTrue()
    }

    @Test
    fun isLocked_false_after_markUnlocked_within_window() = runBlocking {
        lockManager.setPin("1234".toCharArray())
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
