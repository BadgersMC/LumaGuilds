package net.lumalyte.lg.domain.entities

import net.lumalyte.lg.domain.values.Position3D
import java.time.Instant
import java.util.UUID

/**
 * Represents a guild in the system.
 *
 * @property id The unique identifier for the guild.
 * @property name The name of the guild.
 * @property banner The banner ItemStack data serialized as string for the guild.
 * @property emoji The Nexo emoji placeholder for the guild tag (e.g., ":catsmileysmile:").
 * @property tag The custom display tag for the guild (supports MiniMessage formatting).
 * @property description The description of the guild.
 * @property homes The home locations of the guild.
 * @property level The current level of the guild.
 * @property bankBalance The current bank balance of the guild.
 * @property mode The current mode of the guild (Peaceful or Hostile).
 * @property modeChangedAt The timestamp when the mode was last changed.
 * @property createdAt The timestamp when the guild was created.
 */
data class Guild(
    val id: UUID,
    val name: String,
    val ownerId: UUID,
    val ownerName: String,
    val banner: String? = null,
    val emoji: String? = null,
    val tag: String? = null,
    val description: String? = null,
    val homes: GuildHomes = GuildHomes.EMPTY,
    val level: Int = 1,
    val bankBalance: Int = 0,
    val mode: GuildMode = GuildMode.HOSTILE,
    val modeChangedAt: Instant? = null,
    val createdAt: Instant
) {
    init {
        require(name.length in 1..32) { "Guild name must be between 1 and 32 characters." }
        require(level > 0) { "Guild level must be positive." }
        require(bankBalance >= 0) { "Guild bank balance cannot be negative." }
        
        // Validate emoji format if provided (should be Nexo placeholder format like ":emojiname:")
        emoji?.let { emojiValue ->
            require(emojiValue.startsWith(":") && emojiValue.endsWith(":") && emojiValue.length > 2) {
                "Guild emoji must be a valid Nexo placeholder format (e.g., ':catsmileysmile:')"
            }
        }

        // Validate description length if provided
        description?.let { descValue ->
            require(descValue.length <= 100) {
                "Guild description must be 100 characters or less."
            }
        }
    }

    /**
     * Gets the default/main home for backward compatibility.
     * @deprecated Use homes.defaultHome instead for new code.
     */
    val home: GuildHome?
        get() = homes.defaultHome
}

/**
 * Represents the home location of a guild.
 *
 * @property worldId The unique identifier of the world.
 * @property position The position coordinates in the world.
 */
data class GuildHome(
    val worldId: UUID,
    val position: Position3D
)

/**
 * Represents multiple home locations for a guild with names/identifiers.
 *
 * @property homes A map of home names to their locations.
 */
data class GuildHomes(
    val homes: Map<String, GuildHome> = emptyMap()
) {
    /**
     * Gets the default/main home (first one if no "main" exists).
     */
    val defaultHome: GuildHome?
        get() = homes["main"] ?: homes.values.firstOrNull()

    /**
     * Gets all home names.
     */
    val homeNames: Set<String>
        get() = homes.keys

    /**
     * Gets a specific home by name.
     */
    fun getHome(name: String): GuildHome? = homes[name]

    /**
     * Adds or updates a home.
     */
    fun withHome(name: String, home: GuildHome): GuildHomes {
        val newHomes = homes.toMutableMap()
        newHomes[name] = home
        return GuildHomes(newHomes)
    }

    /**
     * Removes a home by name.
     */
    fun withoutHome(name: String): GuildHomes {
        val newHomes = homes.toMutableMap()
        newHomes.remove(name)
        return GuildHomes(newHomes)
    }

    /**
     * Checks if the guild has any homes.
     */
    fun hasHomes(): Boolean = homes.isNotEmpty()

    /**
     * Gets the number of homes set.
     */
    val size: Int
        get() = homes.size

    companion object {
        val EMPTY = GuildHomes(emptyMap())
    }
}

/**
 * Represents the mode of a guild.
 */
enum class GuildMode {
    PEACEFUL,
    HOSTILE
}

/**
 * Represents a diplomatic relation between two guilds.
 *
 * @property id The unique identifier for the diplomatic relation.
 * @property guildId The ID of the initiating guild.
 * @property targetGuildId The ID of the target guild.
 * @property type The type of diplomatic relation (ALLIANCE, ENEMY, TRUCE, NEUTRAL).
 * @property establishedAt The timestamp when the relation was established.
 * @property expiresAt The timestamp when the relation expires (for truces).
 * @property metadata Additional metadata for the relation.
 */
data class DiplomaticRelation(
    val id: UUID,
    val guildId: UUID,
    val targetGuildId: UUID,
    val type: DiplomaticRelationType,
    val establishedAt: Instant,
    val expiresAt: Instant? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    fun isActive(): Boolean {
        return expiresAt == null || expiresAt.isAfter(Instant.now())
    }
}

/**
 * Represents the type of diplomatic relation.
 */
enum class DiplomaticRelationType {
    ALLIANCE,
    ENEMY,
    TRUCE,
    NEUTRAL
}

/**
 * Represents the type of diplomatic request.
 */
enum class DiplomaticRequestType {
    ALLIANCE_REQUEST,
    TRUCE_REQUEST,
    WAR_DECLARATION
}

/**
 * Represents a diplomatic request between guilds.
 * @property id The unique identifier for the request.
 * @property fromGuildId The ID of the guild sending the request.
 * @property toGuildId The ID of the guild receiving the request.
 * @property type The type of diplomatic request.
 * @property message Optional message from the requesting guild.
 * @property requestedAt When the request was made.
 * @property expiresAt When the request expires.
 * @property metadata Additional metadata for the request.
 */
data class DiplomaticRequest(
    val id: UUID,
    val fromGuildId: UUID,
    val toGuildId: UUID,
    val type: DiplomaticRequestType,
    val message: String? = null,
    val requestedAt: Instant,
    val expiresAt: Instant,
    val metadata: Map<String, Any> = emptyMap()
) {
    fun isExpired(): Boolean = expiresAt.isBefore(Instant.now())
    fun isActive(): Boolean = !isExpired()
}

/**
 * Represents a diplomatic history event.
 * @property id The unique identifier for the history event.
 * @property guildId The ID of the guild involved in the event.
 * @property targetGuildId The ID of the other guild involved (if applicable).
 * @property eventType The type of diplomatic event (alliance_formed, war_declared, etc.).
 * @property description A description of what happened.
 * @property timestamp When the event occurred.
 * @property metadata Additional metadata for the event.
 */
data class DiplomaticHistory(
    val id: UUID,
    val guildId: UUID,
    val targetGuildId: UUID?,
    val eventType: String,
    val description: String,
    val timestamp: Instant,
    val metadata: Map<String, Any> = emptyMap()
)

