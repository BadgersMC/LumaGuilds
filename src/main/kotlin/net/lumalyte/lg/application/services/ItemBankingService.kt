package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.GuildChest
import net.lumalyte.lg.domain.entities.GuildChestAccessLog
import net.lumalyte.lg.domain.entities.GuildChestAction
import net.lumalyte.lg.domain.entities.GuildChestContents
import java.util.UUID

/**
 * Service interface for managing guild item banking system with comprehensive auditing.
 */
interface ItemBankingService {

    /**
     * Creates a new guild chest at the specified location.
     *
     * @param guildId The ID of the guild.
     * @param worldId The ID of the world.
     * @param x The X coordinate.
     * @param y The Y coordinate.
     * @param z The Z coordinate.
     * @param playerId The ID of the player creating the chest.
     * @return The created guild chest if successful, null otherwise.
     */
    fun createGuildChest(guildId: UUID, worldId: UUID, x: Int, y: Int, z: Int, playerId: UUID): GuildChest?

    /**
     * Gets a guild chest by its location.
     *
     * @param worldId The ID of the world.
     * @param x The X coordinate.
     * @param y The Y coordinate.
     * @param z The Z coordinate.
     * @return The guild chest at the location, null if none exists.
     */
    fun getGuildChestAt(worldId: UUID, x: Int, y: Int, z: Int): GuildChest?

    /**
     * Gets all guild chests for a specific guild.
     *
     * @param guildId The ID of the guild.
     * @return A list of guild chests owned by the guild.
     */
    fun getGuildChests(guildId: UUID): List<GuildChest>

    /**
     * Removes a guild chest.
     *
     * @param chestId The ID of the chest to remove.
     * @param playerId The ID of the player removing the chest.
     * @return true if the chest was removed successfully, false otherwise.
     */
    fun removeGuildChest(chestId: UUID, playerId: UUID): Boolean

    /**
     * Checks if a player can access a guild chest.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @param worldId The ID of the world.
     * @param x The X coordinate.
     * @param y The Y coordinate.
     * @param z The Z coordinate.
     * @return true if the player can access the chest, false otherwise.
     */
    fun canAccessGuildChest(playerId: UUID, guildId: UUID, worldId: UUID, x: Int, y: Int, z: Int): Boolean

    /**
     * Logs a guild chest access attempt.
     *
     * @param chestId The ID of the chest.
     * @param playerId The ID of the player.
     * @param action The action performed.
     * @param itemType The type of item involved (if applicable).
     * @param itemAmount The amount of items involved (if applicable).
     * @return true if the log was created successfully, false otherwise.
     */
    fun logChestAccess(chestId: UUID, playerId: UUID, action: GuildChestAction, itemType: String? = null, itemAmount: Int = 0): Boolean

    /**
     * Gets access logs for a specific chest.
     *
     * @param chestId The ID of the chest.
     * @return A list of access logs for the chest.
     */
    fun getChestAccessLogs(chestId: UUID): List<GuildChestAccessLog>

    /**
     * Gets access logs for a specific player.
     *
     * @param playerId The ID of the player.
     * @return A list of access logs for the player.
     */
    fun getPlayerAccessLogs(playerId: UUID): List<GuildChestAccessLog>

    /**
     * Gets suspicious access logs (break attempts, unauthorized access).
     *
     * @return A list of suspicious access logs.
     */
    fun getSuspiciousAccessLogs(): List<GuildChestAccessLog>

    /**
     * Checks if a guild chest is in a denied region (WorldGuard integration).
     *
     * @param worldId The ID of the world.
     * @param x The X coordinate.
     * @param y The Y coordinate.
     * @param z The Z coordinate.
     * @return true if the location is in a denied region, false otherwise.
     */
    fun isInDeniedRegion(worldId: UUID, x: Int, y: Int, z: Int): Boolean

    /**
     * Gets the maximum chest size for a guild based on their level.
     *
     * @param guildId The ID of the guild.
     * @return The maximum chest size.
     */
    fun getMaxChestSize(guildId: UUID): Int

    /**
     * Auto-enemies guilds when a chest is broken by a member of another guild.
     *
     * @param victimGuildId The ID of the guild that owned the chest.
     * @param attackerGuildId The ID of the guild that broke the chest.
     * @param reason The reason for the auto-enemy.
     * @return true if the guilds were auto-enemied, false otherwise.
     */
    fun autoEnemyGuilds(victimGuildId: UUID, attackerGuildId: UUID, reason: String): Boolean

    /**
     * Gets the current item banking configuration.
     *
     * @return The item banking configuration.
     */
    fun getItemBankingConfig(): net.lumalyte.lg.config.ItemBankingConfig

    /**
     * Converts an item to its monetary value.
     *
     * @param itemType The type of item.
     * @param amount The amount of items.
     * @return The monetary value of the items.
     */
    fun getItemValue(itemType: String, amount: Int): Double

    /**
     * Gets the default currency item.
     *
     * @return The default currency item type.
     */
    fun getDefaultCurrencyItem(): String

    /**
     * Updates the last accessed time for a chest.
     *
     * @param chestId The ID of the chest.
     * @param accessTime The time of access.
     * @return true if the update was successful, false otherwise.
     */
    fun updateLastAccessed(chestId: UUID, accessTime: java.time.Instant): Boolean
}
