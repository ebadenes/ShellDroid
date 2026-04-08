package io.shelldroid.core.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.shelldroid.core.db.entities.ConnectionGroup
import io.shelldroid.core.db.entities.HostGroupMembership
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionGroupDao {
    @Query("SELECT * FROM connection_groups WHERE userId = :userId ORDER BY name")
    fun observeAll(userId: String): Flow<List<ConnectionGroup>>

    @Query("SELECT * FROM connection_groups WHERE id = :id")
    suspend fun findById(id: String): ConnectionGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: ConnectionGroup)

    @Delete
    suspend fun delete(group: ConnectionGroup)

    @Query("DELETE FROM connection_groups WHERE id = :id")
    suspend fun deleteById(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMembership(membership: HostGroupMembership)

    @Delete
    suspend fun removeMembership(membership: HostGroupMembership)

    @Query("SELECT * FROM host_group_memberships WHERE groupId = :groupId")
    suspend fun membershipsOfGroup(groupId: String): List<HostGroupMembership>
}
