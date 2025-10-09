package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.DiplomaticRequest
import net.lumalyte.lg.domain.entities.DiplomaticRequestType
import java.util.UUID

/**
 * Repository interface for managing diplomatic requests persistence.
 */
interface DiplomaticRequestRepository {

    /**
     * Adds a new diplomatic request to the repository.
     *
     * @param request The request to add.
     * @return true if the request was added successfully, false otherwise.
     */
    fun add(request: DiplomaticRequest): Boolean

    /**
     * Updates an existing diplomatic request in the repository.
     *
     * @param request The request to update.
     * @return true if the request was updated successfully, false otherwise.
     */
    fun update(request: DiplomaticRequest): Boolean

    /**
     * Removes a diplomatic request from the repository.
     *
     * @param requestId The ID of the request to remove.
     * @return true if the request was removed successfully, false otherwise.
     */
    fun remove(requestId: UUID): Boolean

    /**
     * Gets a diplomatic request by its ID.
     *
     * @param requestId The ID of the request to retrieve.
     * @return The request if found, null otherwise.
     */
    fun getById(requestId: UUID): DiplomaticRequest?

    /**
     * Gets all diplomatic requests for a specific guild.
     *
     * @param guildId The ID of the guild to get requests for.
     * @return A list of requests involving the guild.
     */
    fun getByGuild(guildId: UUID): List<DiplomaticRequest>

    /**
     * Gets incoming requests for a specific guild.
     *
     * @param guildId The ID of the guild receiving the requests.
     * @return A list of incoming requests.
     */
    fun getIncomingRequests(guildId: UUID): List<DiplomaticRequest>

    /**
     * Gets outgoing requests from a specific guild.
     *
     * @param guildId The ID of the guild sending the requests.
     * @return A list of outgoing requests.
     */
    fun getOutgoingRequests(guildId: UUID): List<DiplomaticRequest>

    /**
     * Gets diplomatic requests by type for a specific guild.
     *
     * @param guildId The ID of the guild.
     * @param type The type of request to filter by.
     * @return A list of requests of the specified type.
     */
    fun getByType(guildId: UUID, type: DiplomaticRequestType): List<DiplomaticRequest>

    /**
     * Gets all active diplomatic requests for a specific guild.
     *
     * @param guildId The ID of the guild to get requests for.
     * @return A list of active requests involving the guild.
     */
    fun getActiveRequests(guildId: UUID): List<DiplomaticRequest>

    /**
     * Gets all expired diplomatic requests that need cleanup.
     *
     * @return A list of requests that have expired and should be processed.
     */
    fun getExpiredRequests(): List<DiplomaticRequest>

    /**
     * Gets diplomatic requests between two specific guilds.
     *
     * @param guildA The first guild ID.
     * @param guildB The second guild ID.
     * @return A list of requests between the guilds.
     */
    fun getBetweenGuilds(guildA: UUID, guildB: UUID): List<DiplomaticRequest>

    /**
     * Gets all diplomatic requests in the repository.
     *
     * @return A list of all requests.
     */
    fun getAll(): List<DiplomaticRequest>
}
