package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.GuildMode
import java.time.Duration
import java.util.UUID

/**
 * Service interface for managing guild mode switching and validation.
 */
interface ModeService {

    /**
     * Sets the mode of a guild.
     *
     * @param guildId The ID of the guild.
     * @param newMode The new mode to set.
     * @param actorId The ID of the player performing the action.
     * @return The updated guild if successful, null otherwise.
     */
    fun setGuildMode(guildId: UUID, newMode: GuildMode, actorId: UUID): net.lumalyte.lg.domain.entities.Guild?

    /**
     * Gets the current mode of a guild.
     *
     * @param guildId The ID of the guild.
     * @return The current mode of the guild.
     */
    fun getGuildMode(guildId: UUID): GuildMode

    /**
     * Checks if a guild can switch to a specific mode.
     *
     * @param guildId The ID of the guild.
     * @param targetMode The target mode to switch to.
     * @return true if the guild can switch to the mode, false otherwise.
     */
    fun canSwitchToMode(guildId: UUID, targetMode: GuildMode): Boolean

    /**
     * Checks if a guild is currently on cooldown from a mode switch.
     *
     * @param guildId The ID of the guild.
     * @return true if the guild is on cooldown, false otherwise.
     */
    fun isModeSwitchOnCooldown(guildId: UUID): Boolean

    /**
     * Gets the remaining cooldown time for mode switching.
     *
     * @param guildId The ID of the guild.
     * @return The remaining cooldown duration, or zero if not on cooldown.
     */
    fun getModeSwitchCooldown(guildId: UUID): Duration

    /**
     * Checks if a player can change the mode of their guild.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @return true if the player can change the mode, false otherwise.
     */
    fun canPlayerChangeMode(playerId: UUID, guildId: UUID): Boolean

    /**
     * Validates if PvP is allowed between two players based on their guild modes.
     *
     * @param attackerId The ID of the attacking player.
     * @param victimId The ID of the victim player.
     * @return true if PvP is allowed, false otherwise.
     */
    fun isPvpAllowed(attackerId: UUID, victimId: UUID): Boolean

    /**
     * Validates if PvP is allowed in a guild's territory.
     *
     * @param playerId The ID of the player.
     * @param territoryGuildId The ID of the guild that owns the territory.
     * @return true if PvP is allowed in the territory, false otherwise.
     */
    fun isPvpAllowedInTerritory(playerId: UUID, territoryGuildId: UUID): Boolean

    /**
     * Checks if a guild can participate in wars.
     *
     * @param guildId The ID of the guild.
     * @return true if the guild can participate in wars, false otherwise.
     */
    fun canGuildParticipateInWars(guildId: UUID): Boolean

    /**
     * Gets the mode switch cooldown duration from configuration.
     *
     * @return The cooldown duration.
     */
    fun getModeSwitchCooldownDuration(): Duration

    /**
     * Checks if a guild is currently in an active war.
     *
     * @param guildId The ID of the guild.
     * @return true if the guild is in an active war, false otherwise.
     */
    fun isGuildInActiveWar(guildId: UUID): Boolean

    /**
     * Validates if a war request can be sent to a target guild based on modes.
     *
     * @param requestingGuildId The ID of the requesting guild.
     * @param targetGuildId The ID of the target guild.
     * @return true if the war request is allowed, false otherwise.
     */
    fun canSendWarRequest(requestingGuildId: UUID, targetGuildId: UUID): Boolean

    /**
     * Gets the reason why a guild cannot switch to a specific mode.
     *
     * @param guildId The ID of the guild.
     * @param targetMode The target mode.
     * @return The reason why the switch is not allowed, or null if allowed.
     */
    fun getModeSwitchBlockReason(guildId: UUID, targetMode: GuildMode): String?
}
