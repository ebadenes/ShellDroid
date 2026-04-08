package io.shelldroid.core.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.shelldroid.core.db.entities.Host
import kotlinx.coroutines.flow.Flow

@Dao
interface HostDao {
    @Query("SELECT * FROM hosts WHERE userId = :userId ORDER BY name")
    fun observeAll(userId: String): Flow<List<Host>>

    @Query("SELECT * FROM hosts WHERE id = :id")
    suspend fun findById(id: String): Host?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(host: Host)

    @Delete
    suspend fun delete(host: Host)

    @Query("DELETE FROM hosts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE hosts SET lastConnectedAt = :timestamp WHERE id = :id")
    suspend fun updateLastConnectedAt(id: String, timestamp: Long)
}
