package io.shelldroid.core.db

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.shelldroid.core.db.dao.ConnectionGroupDao
import io.shelldroid.core.db.dao.DeletedItemDao
import io.shelldroid.core.db.dao.HostDao
import io.shelldroid.core.db.dao.IdentityDao
import io.shelldroid.core.db.dao.KnownHostDao
import io.shelldroid.core.db.dao.PortForwardDao
import io.shelldroid.core.db.dao.SnippetDao
import io.shelldroid.core.db.dao.UserDao
import javax.inject.Singleton

private val Context.shelldroidDataStore: DataStore<Preferences> by preferencesDataStore(name = "shelldroid_prefs")

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    db.execSQL(
                        "INSERT INTO users (id, name, createdAt) VALUES (?, ?, ?)",
                        arrayOf(
                            AppDatabase.DEFAULT_USER_ID,
                            AppDatabase.DEFAULT_USER_NAME,
                            System.currentTimeMillis(),
                        ),
                    )
                }
            })
            .build()

    @Provides
    @Singleton
    @UserPrefsDataStore
    fun provideDataStore(@ApplicationContext ctx: Context): DataStore<Preferences> =
        ctx.shelldroidDataStore

    @Provides fun userDao(db: AppDatabase): UserDao = db.userDao()
    @Provides fun hostDao(db: AppDatabase): HostDao = db.hostDao()
    @Provides fun identityDao(db: AppDatabase): IdentityDao = db.identityDao()
    @Provides fun knownHostDao(db: AppDatabase): KnownHostDao = db.knownHostDao()
    @Provides fun snippetDao(db: AppDatabase): SnippetDao = db.snippetDao()
    @Provides fun portForwardDao(db: AppDatabase): PortForwardDao = db.portForwardDao()
    @Provides fun connectionGroupDao(db: AppDatabase): ConnectionGroupDao = db.connectionGroupDao()
    @Provides fun deletedItemDao(db: AppDatabase): DeletedItemDao = db.deletedItemDao()
}
