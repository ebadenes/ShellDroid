package io.shelldroid.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.shelldroid.core.db.entities.DeletedItem
import kotlinx.coroutines.flow.Flow

@Dao
interface DeletedItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DeletedItem)

    @Query("SELECT * FROM deleted_items WHERE userId = :userId ORDER BY deletedAt")
    fun observeAll(userId: String): Flow<List<DeletedItem>>

    @Query("DELETE FROM deleted_items WHERE id = :id")
    suspend fun deleteById(id: String)
}
