package io.shelldroid.core.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hosts",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Identity::class,
            parentColumns = ["id"],
            childColumns = ["identityId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("userId"), Index("identityId")],
)
data class Host(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val identityId: String? = null,
    @ColumnInfo(defaultValue = "") val autoCommand: String = "",
    val createdAt: Long,
    val lastConnectedAt: Long? = null,
    /**
     * Quick Connect hosts are persisted with `ephemeral = true` so the
     * terminal can resolve them via [HostDao.findById] (title, identity,
     * etc.). When the user disconnects from the bridge, ephemeral hosts
     * are deleted automatically. Set `false` if the user explicitly
     * checks "Save connection" in the Quick Connect dialog.
     */
    @ColumnInfo(defaultValue = "0") val ephemeral: Boolean = false,
)
