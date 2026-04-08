package io.shelldroid.core.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.shelldroid.core.db.entities.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY createdAt")
    fun observeAll(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun findById(id: String): User?

    @Query("SELECT * FROM users WHERE id = 'default' LIMIT 1")
    suspend fun findDefault(): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: User)

    @Delete
    suspend fun delete(user: User)
}
