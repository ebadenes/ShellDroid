package io.shelldroid.core.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.shelldroid.core.db.entities.Snippet
import kotlinx.coroutines.flow.Flow

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets WHERE userId = :userId ORDER BY name")
    fun observeAll(userId: String): Flow<List<Snippet>>

    @Query("SELECT * FROM snippets WHERE id = :id")
    suspend fun findById(id: String): Snippet?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snippet: Snippet)

    @Delete
    suspend fun delete(snippet: Snippet)

    @Query("DELETE FROM snippets WHERE id = :id")
    suspend fun deleteById(id: String)
}
