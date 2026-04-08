package io.shelldroid.core.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.shelldroid.core.db.TombstoneType

@Entity(
    tableName = "deleted_items",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("userId"), Index("entityType")],
)
data class DeletedItem(
    @PrimaryKey val id: String,
    val userId: String,
    val entityType: TombstoneType,
    val entityId: String,
    val deletedAt: Long,
)
