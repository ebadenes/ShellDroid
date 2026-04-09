package io.shelldroid.core.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.shelldroid.core.db.entities.KnownHost
import kotlinx.coroutines.flow.Flow

@Dao
interface KnownHostDao {
    @Query("SELECT * FROM known_hosts WHERE userId = :userId ORDER BY hostname")
    fun observeAll(userId: String): Flow<List<KnownHost>>

    @Query("SELECT * FROM known_hosts WHERE userId = :userId AND hostname = :hostname AND port = :port LIMIT 1")
    suspend fun find(userId: String, hostname: String, port: Int): KnownHost?

    @Query("SELECT * FROM known_hosts WHERE id = :id")
    suspend fun findById(id: String): KnownHost?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(knownHost: KnownHost)

    @Delete
    suspend fun delete(knownHost: KnownHost)

    @Query("UPDATE known_hosts SET lastSeen = :timestamp WHERE id = :id")
    suspend fun updateLastSeen(id: String, timestamp: Long)

    @Query("DELETE FROM known_hosts")
    suspend fun deleteAll()
}
