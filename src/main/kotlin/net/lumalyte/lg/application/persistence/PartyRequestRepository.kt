package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.PartyRequest
import net.lumalyte.lg.domain.entities.PartyRequestStatus
import java.util.UUID

/**
 * Repository interface for managing party request persistence.
 */
interface PartyRequestRepository {
    
    /**
     * Adds a new party request to the repository.
     *
     * @param request The party request to add.
     * @return true if the request was added successfully, false otherwise.
     */
    fun add(request: PartyRequest): Boolean
    
    /**
     * Updates an existing party request in the repository.
     *
     * @param request The party request to update.
     * @return true if the request was updated successfully, false otherwise.
     */
    fun update(request: PartyRequest): Boolean
    
    /**
     * Removes a party request from the repository.
     *
     * @param requestId The ID of the request to remove.
     * @return true if the request was removed successfully, false otherwise.
     */
    fun remove(requestId: UUID): Boolean
    
    /**
     * Gets a party request by its ID.
     *
     * @param requestId The ID of the request to retrieve.
     * @return The party request if found, null otherwise.
     */
    fun getById(requestId: UUID): PartyRequest?
    
    /**
     * Gets all pending party requests for a specific guild (incoming).
     *
     * @param guildId The ID of the guild.
     * @return A set of pending requests to the guild.
     */
    fun getPendingRequestsForGuild(guildId: UUID): Set<PartyRequest>
    
    /**
     * Gets all pending party requests from a specific guild (outgoing).
     *
     * @param guildId The ID of the guild.
     * @return A set of pending requests from the guild.
     */
    fun getPendingRequestsFromGuild(guildId: UUID): Set<PartyRequest>
    
    /**
     * Gets all party requests sent by a specific player.
     *
     * @param playerId The ID of the player.
     * @return A set of party requests made by the player.
     */
    fun getRequestsByRequester(playerId: UUID): Set<PartyRequest>
    
    /**
     * Gets all party requests with a specific status.
     *
     * @param status The status to filter by.
     * @return A set of requests with the specified status.
     */
    fun getRequestsByStatus(status: PartyRequestStatus): Set<PartyRequest>
    
    /**
     * Gets all expired party requests that need cleanup.
     *
     * @return A set of requests that have expired.
     */
    fun getExpiredRequests(): Set<PartyRequest>
    
    /**
     * Finds an active party request between two guilds.
     *
     * @param fromGuildId The ID of the requesting guild.
     * @param toGuildId The ID of the target guild.
     * @return The active request if found, null otherwise.
     */
    fun findActiveRequestBetweenGuilds(fromGuildId: UUID, toGuildId: UUID): PartyRequest?
    
    /**
     * Gets all party requests in the repository.
     *
     * @return A set of all party requests.
     */
    fun getAll(): Set<PartyRequest>
}
