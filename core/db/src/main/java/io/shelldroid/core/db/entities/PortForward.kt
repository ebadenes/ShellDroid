package io.shelldroid.core.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.shelldroid.core.db.PortForwardType

@Entity(
    tableName = "port_forwards",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Host::class,
            parentColumns = ["id"],
            childColumns = ["hostId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("userId"), Index("hostId")],
)
data class PortForward(
    @PrimaryKey val id: String,
    val userId: String,
    val hostId: String,
    val type: PortForwardType,
    val localPort: Int,
    val remoteHost: String? = null,
    val remotePort: Int? = null,
    val autoStart: Boolean = false,
    val createdAt: Long,
)
