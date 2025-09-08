package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.RankPermission
import java.util.UUID

/**
 * Service interface for managing guild operations.
 */
interface GuildService {
    /**
     * Creates a new guild.
     *
     * @param name The name of the guild.
     * @param ownerId The ID of the player creating the guild.
     * @param banner The banner material name for the guild.
     * @return The created guild, or null if creation failed.
     */
    fun createGuild(name: String, ownerId: UUID, banner: String? = null): Guild?

    /**
     * Disbands a guild.
     *
     * @param guildId The ID of the guild to disband.
     * @param actorId The ID of the player performing the action.
     * @return true if successful, false otherwise.
     */
    fun disbandGuild(guildId: UUID, actorId: UUID): Boolean

    /**
     * Renames a guild.
     *
     * @param guildId The ID of the guild to rename.
     * @param newName The new name for the guild.
     * @param actorId The ID of the player performing the action.
     * @return true if successful, false otherwise.
     */
    fun renameGuild(guildId: UUID, newName: String, actorId: UUID): Boolean

    /**
     * Sets the banner for a guild.
     *
     * @param guildId The ID of the guild.
     * @param banner The banner ItemStack, or null to clear the banner.
     * @param actorId The ID of the player performing the action.
     * @return true if successful, false otherwise.
     */
    fun setBanner(guildId: UUID, banner: org.bukkit.inventory.ItemStack?, actorId: UUID): Boolean

    /**
     * Sets the emoji for a guild.
     *
     * @param guildId The ID of the guild.
     * @param emoji The Nexo emoji placeholder (e.g., ":catsmileysmile:").
     * @param actorId The ID of the player performing the action.
     * @return true if successful, false otherwise.
     */
    fun setEmoji(guildId: UUID, emoji: String?, actorId: UUID): Boolean

    /**
     * Gets the emoji for a guild.
     *
     * @param guildId The ID of the guild.
     * @return The emoji placeholder, or null if not set.
     */
    fun getEmoji(guildId: UUID): String?

    /**
     * Sets the tag for a guild.
     *
     * @param guildId The ID of the guild.
     * @param tag The custom tag with MiniMessage formatting (e.g., "<gradient:#FF0000:#00FF00>MyGuild").
     * @param actorId The ID of the player performing the action.
     * @return true if successful, false otherwise.
     */
    fun setTag(guildId: UUID, tag: String?, actorId: UUID): Boolean

    /**
     * Gets the tag for a guild.
     *
     * @param guildId The ID of the guild.
     * @return The custom tag, or null if not set.
     */
    fun getTag(guildId: UUID): String?

    /**
     * Sets the home location for a guild.
     *
     * @param guildId The ID of the guild.
     * @param home The home location.
     * @param actorId The ID of the player performing the action.
     * @return true if successful, false otherwise.
     */
    fun setHome(guildId: UUID, home: GuildHome, actorId: UUID): Boolean

    /**
     * Gets the home location for a guild.
     *
     * @param guildId The ID of the guild.
     * @return The home location, or null if not set.
     */
    fun getHome(guildId: UUID): GuildHome?

    /**
     * Removes the home location for a guild.
     *
     * @param guildId The ID of the guild.
     * @param actorId The ID of the player performing the action.
     * @return true if successful, false otherwise.
     */
    fun removeHome(guildId: UUID, actorId: UUID): Boolean

    /**
     * Sets the mode for a guild (Peaceful/Hostile).
     *
     * @param guildId The ID of the guild.
     * @param mode The new mode.
     * @param actorId The ID of the player performing the action.
     * @return true if successful, false otherwise.
     */
    fun setMode(guildId: UUID, mode: GuildMode, actorId: UUID): Boolean

    /**
     * Gets the mode for a guild.
     *
     * @param guildId The ID of the guild.
     * @return The current mode.
     */
    fun getMode(guildId: UUID): GuildMode

    /**
     * Gets a guild by ID.
     *
     * @param guildId The ID of the guild.
     * @return The guild, or null if not found.
     */
    fun getGuild(guildId: UUID): Guild?

    /**
     * Gets a guild by name.
     *
     * @param name The name of the guild.
     * @return The guild, or null if not found.
     */
    fun getGuildByName(name: String): Guild?

    /**
     * Gets all guilds that a player is a member of.
     *
     * @param playerId The ID of the player.
     * @return A set of guilds the player belongs to.
     */
    fun getPlayerGuilds(playerId: UUID): Set<Guild>

    /**
     * Checks if a player is a member of a specific guild.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @return true if the player is a member, false otherwise.
     */
    fun isPlayerInGuild(playerId: UUID, guildId: UUID): Boolean

    /**
     * Checks if a player has permission to perform an action in a guild.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @param permission The permission to check.
     * @return true if the player has permission, false otherwise.
     */
    fun hasPermission(playerId: UUID, guildId: UUID, permission: net.lumalyte.lg.domain.entities.RankPermission): Boolean

    /**
     * Gets the total number of guilds.
     *
     * @return The total count of guilds.
     */
    fun getGuildCount(): Int

    /**
     * Gets all guilds in the system.
     *
     * @return A set of all guilds.
     */
    fun getAllGuilds(): Set<Guild>
}
