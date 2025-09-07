package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

/**
 * Represents a party request between guilds.
 *
 * @property id The unique identifier for the party request.
 * @property fromGuildId The ID of the guild making the request.
 * @property toGuildId The ID of the guild being invited.
 * @property requesterId The ID of the player making the request.
 * @property message Optional message with the request.
 * @property status The current status of the request.
 * @property createdAt The timestamp when the request was made.
 * @property expiresAt The timestamp when the request expires.
 */
data class PartyRequest(
    val id: UUID,
    val fromGuildId: UUID,
    val toGuildId: UUID,
    val requesterId: UUID,
    val message: String? = null,
    val status: PartyRequestStatus = PartyRequestStatus.PENDING,
    val createdAt: Instant,
    val expiresAt: Instant
) {
    init {
        require(fromGuildId != toGuildId) { "A guild cannot send a party request to itself." }
        require(expiresAt.isAfter(createdAt)) { "Expiration time must be after creation time." }
        
        message?.let { msg ->
            require(msg.length <= 255) { "Party request message cannot exceed 255 characters." }
        }
    }
    
    /**
     * Checks if the request is still valid (not expired and pending).
     */
    fun isValid(): Boolean {
        return status == PartyRequestStatus.PENDING && expiresAt.isAfter(Instant.now())
    }
    
    /**
     * Checks if the request has expired.
     */
    fun isExpired(): Boolean {
        return expiresAt.isBefore(Instant.now())
    }
    
    companion object {
        /**
         * Default request expiration time (15 minutes).
         */
        val DEFAULT_EXPIRATION_DURATION = java.time.Duration.ofMinutes(15)
        
        /**
         * Creates a new party request with default expiration.
         */
        fun create(
            fromGuildId: UUID,
            toGuildId: UUID,
            requesterId: UUID,
            message: String? = null
        ): PartyRequest {
            val now = Instant.now()
            return PartyRequest(
                id = UUID.randomUUID(),
                fromGuildId = fromGuildId,
                toGuildId = toGuildId,
                requesterId = requesterId,
                message = message,
                createdAt = now,
                expiresAt = now.plus(DEFAULT_EXPIRATION_DURATION)
            )
        }
    }
}

/**
 * Status of a party request.
 */
enum class PartyRequestStatus {
    /** Request is pending acceptance */
    PENDING,
    
    /** Request was accepted */
    ACCEPTED,
    
    /** Request was rejected */
    REJECTED,
    
    /** Request was cancelled by the sender */
    CANCELLED,
    
    /** Request has expired */
    EXPIRED
}
