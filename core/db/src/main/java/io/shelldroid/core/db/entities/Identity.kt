package io.shelldroid.core.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.shelldroid.core.db.AuthType

@Entity(
    tableName = "identities",
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
class Identity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val authType: AuthType,
    val encryptedSecret: ByteArray,
    val encryptedPassphrase: ByteArray? = null,
    val needsReentry: Boolean = false,
    val createdAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Identity) return false
        return id == other.id &&
            userId == other.userId &&
            name == other.name &&
            authType == other.authType &&
            encryptedSecret.contentEquals(other.encryptedSecret) &&
            (encryptedPassphrase?.contentEquals(other.encryptedPassphrase ?: ByteArray(0)) ?: (other.encryptedPassphrase == null)) &&
            needsReentry == other.needsReentry &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var r = id.hashCode()
        r = 31 * r + userId.hashCode()
        r = 31 * r + name.hashCode()
        r = 31 * r + authType.hashCode()
        r = 31 * r + encryptedSecret.contentHashCode()
        r = 31 * r + (encryptedPassphrase?.contentHashCode() ?: 0)
        r = 31 * r + needsReentry.hashCode()
        r = 31 * r + createdAt.hashCode()
        return r
    }
}
