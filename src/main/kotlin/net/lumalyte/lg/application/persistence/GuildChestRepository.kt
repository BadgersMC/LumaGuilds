package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.GuildChest
import net.lumalyte.lg.domain.entities.GuildChestContents
import java.util.UUID

/**
 * Repository interface for managing guild chest persistence.
 */
interface GuildChestRepository {

    /**
     * Adds a new guild chest to the repository.
     *
     * @param chest The chest to add.
     * @return true if the chest was added successfully, false otherwise.
     */
    fun add(chest: GuildChest): Boolean

    /**
     * Updates an existing guild chest in the repository.
     *
     * @param chest The chest to update.
     * @return true if the chest was updated successfully, false otherwise.
     */
    fun update(chest: GuildChest): Boolean

    /**
     * Removes a guild chest from the repository.
     *
     * @param chestId The ID of the chest to remove.
     * @return true if the chest was removed successfully, false otherwise.
     */
    fun remove(chestId: UUID): Boolean

    /**
     * Gets a guild chest by its ID.
     *
     * @param chestId The ID of the chest to retrieve.
     * @return The chest if found, null otherwise.
     */
    fun getById(chestId: UUID): GuildChest?

    /**
     * Gets all guild chests for a specific guild.
     *
     * @param guildId The ID of the guild.
     * @return A list of chests owned by the guild.
     */
    fun getByGuild(guildId: UUID): List<GuildChest>

    /**
     * Gets a guild chest by its location.
     *
     * @param worldId The ID of the world.
     * @param x The X coordinate.
     * @param y The Y coordinate.
     * @param z The Z coordinate.
     * @return The chest at the specified location, null if none exists.
     */
    fun getByLocation(worldId: UUID, x: Int, y: Int, z: Int): GuildChest?

    /**
     * Gets all guild chests in a specific world.
     *
     * @param worldId The ID of the world.
     * @return A list of chests in the world.
     */
    fun getByWorld(worldId: UUID): List<GuildChest>

    /**
     * Gets all guild chests.
     *
     * @return A list of all chests.
     */
    fun getAll(): List<GuildChest>

    /**
     * Checks if a guild already has a chest at a specific location.
     *
     * @param guildId The ID of the guild.
     * @param worldId The ID of the world.
     * @param x The X coordinate.
     * @param y The Y coordinate.
     * @param z The Z coordinate.
     * @return true if the guild has a chest at the location, false otherwise.
     */
    fun hasChestAtLocation(guildId: UUID, worldId: UUID, x: Int, y: Int, z: Int): Boolean

    /**
     * Gets the chest contents for a specific chest.
     *
     * @param chestId The ID of the chest.
     * @return The chest contents if found, null otherwise.
     */
    fun getChestContents(chestId: UUID): GuildChestContents?

    /**
     * Updates the contents of a guild chest.
     *
     * @param contents The new contents for the chest.
     * @return true if the contents were updated successfully, false otherwise.
     */
    fun updateChestContents(contents: GuildChestContents): Boolean

    /**
     * Removes chest contents for a specific chest.
     *
     * @param chestId The ID of the chest.
     * @return true if the contents were removed successfully, false otherwise.
     */
    fun removeChestContents(chestId: UUID): Boolean

    /**
     * Gets guild chests that are due for cleanup (inactive for a long time).
     *
     * @param olderThan Remove chests not accessed since this timestamp.
     * @return A list of chests that should be cleaned up.
     */
    fun getInactiveChests(olderThan: java.time.Instant): List<GuildChest>

    /**
     * Updates the last accessed time for a chest.
     *
     * @param chestId The ID of the chest.
     * @param accessTime The time of access.
     * @return true if the update was successful, false otherwise.
     */
    fun updateLastAccessed(chestId: UUID, accessTime: java.time.Instant): Boolean
}
