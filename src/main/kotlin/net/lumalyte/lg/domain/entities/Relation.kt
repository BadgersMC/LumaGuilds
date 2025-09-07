package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

/**
 * Represents a diplomatic relation between two guilds.
 *
 * @property id The unique identifier for the relation.
 * @property guildA The first guild in the relation.
 * @property guildB The second guild in the relation.
 * @property type The type of relation between the guilds.
 * @property status The current status of the relation (for managing request flows).
 * @property expiresAt Optional expiration time for temporary relations like truces.
 * @property createdAt The timestamp when the relation was created.
 * @property updatedAt The timestamp when the relation was last updated.
 */
data class Relation(
    val id: UUID,
    val guildA: UUID,
    val guildB: UUID,
    val type: RelationType,
    val status: RelationStatus = RelationStatus.ACTIVE,
    val expiresAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt
) {
    init {
        require(guildA != guildB) { "A guild cannot have a relation with itself." }
        // Ensure consistent ordering: guildA should always be "smaller" than guildB
        require(guildA.toString() < guildB.toString()) { 
            "Guild IDs must be ordered consistently (guildA < guildB)." 
        }
        
        // Validate expiration rules
        if (type == RelationType.TRUCE) {
            requireNotNull(expiresAt) { "Truce relations must have an expiration time." }
            require(expiresAt.isAfter(createdAt)) { "Expiration time must be after creation time." }
        }
        
        if (type != RelationType.TRUCE && expiresAt != null) {
            require(false) { "Only truce relations can have an expiration time." }
        }
    }
    
    /**
     * Checks if this relation involves the specified guild.
     */
    fun involves(guildId: UUID): Boolean {
        return guildA == guildId || guildB == guildId
    }
    
    /**
     * Gets the other guild in the relation given one guild ID.
     */
    fun getOtherGuild(guildId: UUID): UUID {
        return when (guildId) {
            guildA -> guildB
            guildB -> guildA
            else -> throw IllegalArgumentException("Guild $guildId is not part of this relation")
        }
    }
    
    /**
     * Checks if the relation is currently active (not expired).
     */
    fun isActive(): Boolean {
        return status == RelationStatus.ACTIVE && 
               (expiresAt == null || expiresAt.isAfter(Instant.now()))
    }
    
    /**
     * Creates a normalized relation ensuring consistent guild ordering.
     */
    companion object {
        fun create(
            id: UUID = UUID.randomUUID(),
            guildA: UUID,
            guildB: UUID,
            type: RelationType,
            status: RelationStatus = RelationStatus.ACTIVE,
            expiresAt: Instant? = null,
            createdAt: Instant = Instant.now()
        ): Relation {
            // Ensure consistent ordering
            val (firstGuild, secondGuild) = if (guildA.toString() < guildB.toString()) {
                guildA to guildB
            } else {
                guildB to guildA
            }
            
            return Relation(
                id = id,
                guildA = firstGuild,
                guildB = secondGuild,
                type = type,
                status = status,
                expiresAt = expiresAt,
                createdAt = createdAt,
                updatedAt = createdAt
            )
        }
    }
}

/**
 * Types of relations between guilds.
 */
enum class RelationType {
    /** Guilds are allied and can support each other */
    ALLY,
    
    /** Guilds are enemies and can engage in warfare */
    ENEMY,
    
    /** Guilds have a temporary ceasefire */
    TRUCE,
    
    /** Guilds have no special relation (default state) */
    NEUTRAL
}

/**
 * Status of a relation request/agreement.
 */
enum class RelationStatus {
    /** Relation is active and in effect */
    ACTIVE,
    
    /** Relation request is pending acceptance from one party */
    PENDING,
    
    /** Relation has expired or been cancelled */
    EXPIRED,
    
    /** Relation request was rejected */
    REJECTED
}
