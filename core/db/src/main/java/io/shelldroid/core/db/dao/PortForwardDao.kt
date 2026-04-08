package io.shelldroid.core.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.shelldroid.core.db.entities.PortForward
import kotlinx.coroutines.flow.Flow

@Dao
interface PortForwardDao {
    @Query("SELECT * FROM port_forwards WHERE userId = :userId ORDER BY localPort")
    fun observeAll(userId: String): Flow<List<PortForward>>

    @Query("SELECT * FROM port_forwards WHERE hostId = :hostId ORDER BY localPort")
    fun observeByHost(hostId: String): Flow<List<PortForward>>

    @Query("SELECT * FROM port_forwards WHERE id = :id")
    suspend fun findById(id: String): PortForward?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(portForward: PortForward)

    @Delete
    suspend fun delete(portForward: PortForward)

    @Query("DELETE FROM port_forwards WHERE id = :id")
    suspend fun deleteById(id: String)
}
