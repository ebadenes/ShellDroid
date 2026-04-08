package io.shelldroid.core.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "host_group_memberships",
    primaryKeys = ["hostId", "groupId"],
    foreignKeys = [
        ForeignKey(
            entity = Host::class,
            parentColumns = ["id"],
            childColumns = ["hostId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ConnectionGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId"), Index("userId")],
)
data class HostGroupMembership(
    val hostId: String,
    val groupId: String,
    val userId: String,
)
