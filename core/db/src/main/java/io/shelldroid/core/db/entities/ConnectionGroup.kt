package io.shelldroid.core.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "connection_groups",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ConnectionGroup::class,
            parentColumns = ["id"],
            childColumns = ["parentGroupId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("userId"), Index("parentGroupId")],
)
data class ConnectionGroup(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val parentGroupId: String? = null,
    val createdAt: Long,
)
