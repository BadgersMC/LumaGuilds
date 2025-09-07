package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.Guild
import java.util.UUID

/**
 * A repository that handles the persistence of Guilds.
 */
interface GuildRepository {
    /**
     * Gets all guilds that exist.
     *
     * @return The set of all guilds.
     */
    fun getAll(): Set<Guild>

    /**
     * Gets a guild by its id.
     *
     * @param id the unique id of the guild.
     * @return The found guild, or null if it doesn't exist.
     */
    fun getById(id: UUID): Guild?

    /**
     * Gets a guild by its name.
     *
     * @param name the name of the guild.
     * @return The found guild, or null if it doesn't exist.
     */
    fun getByName(name: String): Guild?

    /**
     * Gets all guilds that a player is a member of.
     *
     * @param playerId The id of the player to retrieve guilds for.
     * @return A set of guilds the player belongs to.
     */
    fun getByPlayer(playerId: UUID): Set<Guild>

    /**
     * Adds a new guild.
     *
     * @param guild The guild to add.
     * @return true if successful, false otherwise.
     */
    fun add(guild: Guild): Boolean

    /**
     * Updates the data of an existing guild.
     *
     * @param guild The guild to update.
     * @return true if successful, false otherwise.
     */
    fun update(guild: Guild): Boolean

    /**
     * Removes an existing guild.
     *
     * @param guildId The id of the guild to remove.
     * @return true if successful, false otherwise.
     */
    fun remove(guildId: UUID): Boolean

    /**
     * Checks if a guild name is already taken.
     *
     * @param name The name to check.
     * @return true if the name is taken, false otherwise.
     */
    fun isNameTaken(name: String): Boolean

    /**
     * Gets the total number of guilds.
     *
     * @return The total count of guilds.
     */
    fun getCount(): Int
}
