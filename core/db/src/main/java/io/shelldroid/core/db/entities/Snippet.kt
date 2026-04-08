package io.shelldroid.core.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "snippets",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("userId")],
)
data class Snippet(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val command: String,
    val description: String? = null,
    val createdAt: Long,
)
