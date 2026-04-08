package io.shelldroid.core.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.shelldroid.core.db.entities.Identity
import kotlinx.coroutines.flow.Flow

@Dao
interface IdentityDao {
    @Query("SELECT * FROM identities WHERE userId = :userId ORDER BY name")
    fun observeAll(userId: String): Flow<List<Identity>>

    @Query("SELECT * FROM identities WHERE id = :id")
    suspend fun findById(id: String): Identity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(identity: Identity)

    @Delete
    suspend fun delete(identity: Identity)

    @Query("DELETE FROM identities WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE identities SET needsReentry = 1 WHERE userId = :userId")
    suspend fun markAllNeedReentry(userId: String)
}
