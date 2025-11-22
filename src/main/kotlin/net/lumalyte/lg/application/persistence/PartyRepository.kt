package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.PartyStatus
import java.util.UUID

/**
 * Repository interface for managing party persistence.
 */
interface PartyRepository {
    
    /**
     * Adds a new party to the repository.
     *
     * @param party The party to add.
     * @return true if the party was added successfully, false otherwise.
     */
    fun add(party: Party): Boolean
    
    /**
     * Updates an existing party in the repository.
     *
     * @param party The party to update.
     * @return true if the party was updated successfully, false otherwise.
     */
    fun update(party: Party): Boolean
    
    /**
     * Removes a party from the repository.
     *
     * @param partyId The ID of the party to remove.
     * @return true if the party was removed successfully, false otherwise.
     */
    fun remove(partyId: UUID): Boolean
    
    /**
     * Gets a party by its ID.
     *
     * @param partyId The ID of the party to retrieve.
     * @return The party if found, null otherwise.
     */
    fun getById(partyId: UUID): Party?
    
    /**
     * Gets all active parties involving a specific guild.
     *
     * @param guildId The ID of the guild.
     * @return A set of active parties the guild is part of.
     */
    fun getActivePartiesByGuild(guildId: UUID): Set<Party>

    /**
     * Gets all parties (active and inactive) involving a specific guild.
     * This includes both single-guild channels and multi-guild parties.
     *
     * @param guildId The ID of the guild.
     * @return A set of all parties the guild is part of.
     */
    fun getAllPartiesForGuild(guildId: UUID): Set<Party>
    
    /**
     * Gets all parties led by a specific player.
     *
     * @param playerId The ID of the player.
     * @return A set of parties led by the player.
     */
    fun getPartiesByLeader(playerId: UUID): Set<Party>
    
    /**
     * Gets all parties with a specific status.
     *
     * @param status The status to filter by.
     * @return A set of parties with the specified status.
     */
    fun getPartiesByStatus(status: PartyStatus): Set<Party>
    
    /**
     * Gets all expired parties that need cleanup.
     *
     * @return A set of parties that have expired.
     */
    fun getExpiredParties(): Set<Party>
    
    /**
     * Finds a party between specific guilds.
     *
     * @param guildIds The set of guild IDs to search for.
     * @return The party if found, null otherwise.
     */
    fun findPartyByGuilds(guildIds: Set<UUID>): Party?
    
    /**
     * Gets all active parties.
     *
     * @return A set of all active parties.
     */
    fun getAllActiveParties(): Set<Party>
    
    /**
     * Gets all parties in the repository.
     *
     * @return A set of all parties.
     */
    fun getAll(): Set<Party>
}
