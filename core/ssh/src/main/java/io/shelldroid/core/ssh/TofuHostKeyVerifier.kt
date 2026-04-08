package io.shelldroid.core.ssh

import io.shelldroid.core.db.CurrentUserProvider
import io.shelldroid.core.db.dao.KnownHostDao
import io.shelldroid.core.db.entities.KnownHost
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caller-provided UI bridge for TOFU prompts.
 *
 * Implementations live in feature modules (e.g. `:feature:hosts`) — this
 * module only declares the contract.
 */
interface HostKeyPrompter {
    /**
     * First time we see this host. Return `true` to accept and persist,
     * `false` to abort the connection.
     */
    suspend fun promptFirstSeen(
        hostname: String,
        port: Int,
        keyType: String,
        fingerprint: String,
    ): Boolean

    /**
     * The host's key changed since last time we connected. The verifier
     * ALWAYS rejects the connection in this case — this method exists only
     * to surface the alert to the user.
     */
    suspend fun promptKeyChanged(
        hostname: String,
        port: Int,
        oldFingerprint: String,
        newFingerprint: String,
        newKeyType: String,
    )
}

/**
 * Trust-On-First-Use host key verifier.
 *
 * Policy:
 *  - First seen → ask the user via [HostKeyPrompter]; if accepted, persist.
 *  - Match → update `lastSeen` and accept.
 *  - Mismatch → ALWAYS reject. The user must manually delete the
 *    `KnownHost` entry from settings before retrying. We never silently
 *    overwrite a stored fingerprint.
 *
 * Fingerprints are SHA-256 of the raw public key blob, base64-encoded
 * without padding (matches `ssh-keygen -lf` SHA256 format).
 */
@Singleton
class TofuHostKeyVerifier @Inject constructor(
    private val dao: KnownHostDao,
    private val prompter: HostKeyPrompter,
    private val currentUser: CurrentUserProvider,
) {
    suspend fun verify(
        hostname: String,
        port: Int,
        keyType: String,
        publicKeyBlob: ByteArray,
    ): Boolean {
        val fingerprint = sha256Base64(publicKeyBlob)
        val userId = currentUser.current().id
        val known = dao.find(userId, hostname, port)

        return when {
            known == null -> {
                val accepted = prompter.promptFirstSeen(hostname, port, keyType, fingerprint)
                if (accepted) {
                    val now = System.currentTimeMillis()
                    dao.upsert(
                        KnownHost(
                            id = UUID.randomUUID().toString(),
                            userId = userId,
                            hostname = hostname,
                            port = port,
                            keyType = keyType,
                            fingerprintSha256 = fingerprint,
                            publicKeyBlob = publicKeyBlob,
                            firstSeen = now,
                            lastSeen = now,
                        )
                    )
                }
                accepted
            }
            known.fingerprintSha256 == fingerprint -> {
                dao.updateLastSeen(known.id, System.currentTimeMillis())
                true
            }
            else -> {
                prompter.promptKeyChanged(
                    hostname = hostname,
                    port = port,
                    oldFingerprint = known.fingerprintSha256,
                    newFingerprint = fingerprint,
                    newKeyType = keyType,
                )
                false
            }
        }
    }

    companion object {
        /** SHA-256 of [data] encoded as unpadded base64 (RFC 4648 §3.2). */
        fun sha256Base64(data: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(data)
            return Base64.getEncoder().withoutPadding().encodeToString(digest)
        }
    }
}
