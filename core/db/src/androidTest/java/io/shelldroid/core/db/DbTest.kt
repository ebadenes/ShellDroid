package io.shelldroid.core.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.shelldroid.core.db.entities.User
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

/**
 * Base for Room DAO tests. Uses an in-memory Room database.
 *
 * NOTE: originally written as Robolectric JVM unit tests per the TDD plan,
 * but Robolectric 4.13 failed inside the Docker dev sandbox because its
 * MavenArtifactFetcher tries to download Android SDK JARs at runtime and the
 * container has no writable Maven cache/network. Moved to androidTest so the
 * sources compile and, once a device/emulator is wired into CI, execute
 * unchanged under AndroidJUnit4.
 */
@RunWith(AndroidJUnit4::class)
abstract class DbTest {
    protected lateinit var db: AppDatabase

    @Before
    fun setUpDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        runBlocking {
            db.userDao().upsert(
                User(
                    id = AppDatabase.DEFAULT_USER_ID,
                    name = AppDatabase.DEFAULT_USER_NAME,
                    createdAt = 0L,
                )
            )
        }
    }

    @After
    fun tearDownDb() {
        db.close()
    }
}
