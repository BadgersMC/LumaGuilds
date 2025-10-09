package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

/**
 * Represents a party between guilds for coordinated activities.
 *
 * @property id The unique identifier for the party.
 * @property name The name of the party (optional).
 * @property guildIds The set of guild IDs participating in the party.
 * @property leaderId The ID of the player who created/leads the party.
 * @property status The current status of the party.
 * @property createdAt The timestamp when the party was created.
 * @property expiresAt Optional expiration time for the party.
 * @property restrictedRoles Optional set of rank IDs that can join the party (if null, all guild members can join).
 * @property accessLevel The access level for party membership.
 * @property maxMembers Maximum number of members allowed in the party.
 * @property description Optional description of the party.
 * @property metadata Additional metadata for the party.
 */
data class Party(
    val id: UUID,
    val name: String? = null,
    val guildIds: Set<UUID>,
    val leaderId: UUID,
    val status: PartyStatus = PartyStatus.ACTIVE,
    val createdAt: Instant,
    val expiresAt: Instant? = null,
    val restrictedRoles: Set<UUID>? = null,
    val accessLevel: PartyAccessLevel = PartyAccessLevel.OPEN,
    val maxMembers: Int = 50,
    val description: String? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    init {
        require(guildIds.isNotEmpty()) { "A party must have at least 1 guild." }
        require(guildIds.size <= 10) { "A party cannot have more than 10 guilds." }
        
        name?.let { partyName ->
            require(partyName.length in 1..32) { "Party name must be between 1 and 32 characters." }
        }
        
        expiresAt?.let { expiration ->
            require(expiration.isAfter(createdAt)) { "Expiration time must be after creation time." }
        }
    }
    
    /**
     * Checks if the party is currently active.
     */
    fun isActive(): Boolean {
        return status == PartyStatus.ACTIVE && 
               (expiresAt == null || expiresAt.isAfter(Instant.now()))
    }
    
    /**
     * Checks if a guild is part of this party.
     */
    fun includesGuild(guildId: UUID): Boolean {
        return guildIds.contains(guildId)
    }
    
    /**
     * Gets the other guilds in the party (excluding the specified guild).
     */
    fun getOtherGuilds(excludeGuildId: UUID): Set<UUID> {
        return guildIds.minus(excludeGuildId)
    }

    /**
     * Checks if a player can join this party based on their rank.
     * If restrictedRoles is null or empty, all guild members can join.
     * If restrictedRoles is set, only players with those ranks can join.
     */
    fun canPlayerJoin(playerRankId: UUID): Boolean {
        return restrictedRoles.isNullOrEmpty() || restrictedRoles.contains(playerRankId)
    }

    /**
     * Checks if the party has role restrictions.
     */
    fun hasRoleRestrictions(): Boolean {
        return !restrictedRoles.isNullOrEmpty()
    }

    /**
     * Checks if this is a private party (single guild only).
     */
    fun isPrivateParty(): Boolean {
        return guildIds.size == 1
    }
}

/**
 * Status of a party.
 */
enum class PartyStatus {
    /** Party is active and functional */
    ACTIVE,

    /** Party has been dissolved */
    DISSOLVED,

    /** Party has expired */
    EXPIRED
}

/**
 * Access level for party membership.
 */
enum class PartyAccessLevel {
    /** Anyone can join the party */
    OPEN,

    /** Only invited players can join */
    INVITE_ONLY,

    /** Only guild members can join */
    GUILD_ONLY,

    /** Only party leader can invite */
    LEADER_ONLY
}

/**
 * Statistics for a party.
 */
data class PartyStatistics(
    val totalMembers: Int,
    val onlineMembers: Int,
    val guildsInvolved: Int,
    val activityLevel: String,
    val duration: String,
    val activityScore: Int,
    val participationRate: Int,
    val eventsCount: Int,
    val lastActivity: String
)