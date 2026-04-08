package io.shelldroid.core.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "known_hosts",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["userId", "hostname", "port"], unique = true),
    ],
)
class KnownHost(
    @PrimaryKey val id: String,
    val userId: String,
    val hostname: String,
    val port: Int,
    val keyType: String,
    val fingerprintSha256: String,
    val publicKeyBlob: ByteArray,
    val firstSeen: Long,
    val lastSeen: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KnownHost) return false
        return id == other.id &&
            userId == other.userId &&
            hostname == other.hostname &&
            port == other.port &&
            keyType == other.keyType &&
            fingerprintSha256 == other.fingerprintSha256 &&
            publicKeyBlob.contentEquals(other.publicKeyBlob) &&
            firstSeen == other.firstSeen &&
            lastSeen == other.lastSeen
    }

    override fun hashCode(): Int {
        var r = id.hashCode()
        r = 31 * r + userId.hashCode()
        r = 31 * r + hostname.hashCode()
        r = 31 * r + port
        r = 31 * r + keyType.hashCode()
        r = 31 * r + fingerprintSha256.hashCode()
        r = 31 * r + publicKeyBlob.contentHashCode()
        r = 31 * r + firstSeen.hashCode()
        r = 31 * r + lastSeen.hashCode()
        return r
    }
}
