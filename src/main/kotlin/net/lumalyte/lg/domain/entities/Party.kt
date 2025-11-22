package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

/**
 * Represents a party between guilds for coordinated activities.
 * Can be used as guild-internal channels (1 guild) or multi-guild parties (2+ guilds).
 *
 * @property id The unique identifier for the party.
 * @property name The name of the party (optional).
 * @property guildIds The set of guild IDs participating in the party.
 * @property leaderId The ID of the player who created/leads the party.
 * @property status The current status of the party.
 * @property createdAt The timestamp when the party was created.
 * @property expiresAt Optional expiration time for the party.
 * @property restrictedRoles Optional set of rank IDs that can join the party (if null, all guild members can join).
 * @property mutedPlayers Map of player IDs to mute expiration times (null = permanent mute).
 * @property bannedPlayers Set of player IDs permanently banned from this party/channel.
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
    val mutedPlayers: Map<UUID, Instant?> = emptyMap(),
    val bannedPlayers: Set<UUID> = emptySet()
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
     * Private parties function as guild-internal chat channels.
     */
    fun isPrivateParty(): Boolean {
        return guildIds.size == 1
    }

    /**
     * Checks if a player is currently muted in this party.
     * Removes expired mutes automatically.
     */
    fun isPlayerMuted(playerId: UUID): Boolean {
        val muteExpiration = mutedPlayers[playerId] ?: return false

        // Permanent mute (null expiration)
        if (muteExpiration == null) return true

        // Temporary mute - check if expired
        return muteExpiration.isAfter(Instant.now())
    }

    /**
     * Checks if a player is banned from this party.
     */
    fun isPlayerBanned(playerId: UUID): Boolean {
        return bannedPlayers.contains(playerId)
    }

    /**
     * Checks if a player can send messages in this party.
     */
    fun canPlayerSendMessage(playerId: UUID): Boolean {
        return !isPlayerMuted(playerId) && !isPlayerBanned(playerId)
    }

    /**
     * Gets active (non-expired) mutes.
     */
    fun getActiveMutes(): Map<UUID, Instant?> {
        val now = Instant.now()
        return mutedPlayers.filter { (_, expiration) ->
            expiration == null || expiration.isAfter(now)
        }
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
