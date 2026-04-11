package io.shelldroid.core.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.shelldroid.core.db.dao.ConnectionGroupDao
import io.shelldroid.core.db.dao.DeletedItemDao
import io.shelldroid.core.db.dao.HostDao
import io.shelldroid.core.db.dao.IdentityDao
import io.shelldroid.core.db.dao.KnownHostDao
import io.shelldroid.core.db.dao.PortForwardDao
import io.shelldroid.core.db.dao.SnippetDao
import io.shelldroid.core.db.dao.UserDao
import io.shelldroid.core.db.entities.ConnectionGroup
import io.shelldroid.core.db.entities.DeletedItem
import io.shelldroid.core.db.entities.Host
import io.shelldroid.core.db.entities.HostGroupMembership
import io.shelldroid.core.db.entities.Identity
import io.shelldroid.core.db.entities.KnownHost
import io.shelldroid.core.db.entities.PortForward
import io.shelldroid.core.db.entities.Snippet
import io.shelldroid.core.db.entities.User

@Database(
    entities = [
        User::class,
        Host::class,
        Identity::class,
        KnownHost::class,
        Snippet::class,
        PortForward::class,
        ConnectionGroup::class,
        HostGroupMembership::class,
        DeletedItem::class,
    ],
    version = 3,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
    ],
    exportSchema = true,
)
@TypeConverters(
    AuthTypeConverter::class,
    PortForwardTypeConverter::class,
    TombstoneTypeConverter::class,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun hostDao(): HostDao
    abstract fun identityDao(): IdentityDao
    abstract fun knownHostDao(): KnownHostDao
    abstract fun snippetDao(): SnippetDao
    abstract fun portForwardDao(): PortForwardDao
    abstract fun connectionGroupDao(): ConnectionGroupDao
    abstract fun deletedItemDao(): DeletedItemDao

    companion object {
        const val DATABASE_NAME = "shelldroid.db"
        const val DEFAULT_USER_ID = "default"
        const val DEFAULT_USER_NAME = "Default"
    }
}
