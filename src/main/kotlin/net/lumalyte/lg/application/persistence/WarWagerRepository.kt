package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.WarWager
import net.lumalyte.lg.domain.entities.WagerStatus
import java.util.UUID

/**
 * Repository interface for managing war wager persistence.
 */
interface WarWagerRepository {

    /**
     * Adds a new war wager to the repository.
     *
     * @param wager The wager to add.
     * @return true if the wager was added successfully, false otherwise.
     */
    fun add(wager: WarWager): Boolean

    /**
     * Updates an existing war wager in the repository.
     *
     * @param wager The wager to update.
     * @return true if the wager was updated successfully, false otherwise.
     */
    fun update(wager: WarWager): Boolean

    /**
     * Removes a war wager from the repository.
     *
     * @param wagerId The ID of the wager to remove.
     * @return true if the wager was removed successfully, false otherwise.
     */
    fun remove(wagerId: UUID): Boolean

    /**
     * Gets a war wager by its ID.
     *
     * @param wagerId The ID of the wager to retrieve.
     * @return The wager if found, null otherwise.
     */
    fun getById(wagerId: UUID): WarWager?

    /**
     * Gets a war wager by war ID.
     *
     * @param warId The ID of the war.
     * @return The wager if found, null otherwise.
     */
    fun getByWarId(warId: UUID): WarWager?

    /**
     * Gets all war wagers for a guild.
     *
     * @param guildId The ID of the guild.
     * @return List of wagers involving the guild.
     */
    fun getByGuild(guildId: UUID): List<WarWager>

    /**
     * Gets all war wagers with a specific status.
     *
     * @param status The status to filter by.
     * @return List of wagers with the specified status.
     */
    fun getByStatus(status: WagerStatus): List<WarWager>

    /**
     * Gets all war wagers in the repository.
     *
     * @return List of all war wagers.
     */
    fun getAll(): List<WarWager>

    /**
     * Gets pending wagers (in escrow) for a guild.
     *
     * @param guildId The ID of the guild.
     * @return List of pending wagers for the guild.
     */
    fun getPendingWagersForGuild(guildId: UUID): List<WarWager>
}
