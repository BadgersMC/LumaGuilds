package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.PartyRequest
import net.lumalyte.lg.domain.entities.PartyStatistics
import java.time.Duration
import java.util.UUID

/**
 * Service interface for managing guild parties and coordination.
 */
interface PartyService {

    /**
     * Creates a new party with the specified configuration.
     *
     * @param party The party entity to create.
     * @return The created party if successful, null otherwise.
     */
    fun createParty(party: Party): Party?

    /**
     * Sends a party request to another guild.
     *
     * @param fromGuildId The ID of the guild making the request.
     * @param toGuildId The ID of the guild being invited.
     * @param requesterId The ID of the player making the request.
     * @param message Optional message with the request.
     * @return The created party request if successful, null otherwise.
     */
    fun sendPartyRequest(
        fromGuildId: UUID, 
        toGuildId: UUID, 
        requesterId: UUID, 
        message: String? = null
    ): PartyRequest?
    
    /**
     * Accepts a party request and creates/joins a party.
     *
     * @param requestId The ID of the party request to accept.
     * @param acceptingGuildId The ID of the guild accepting the request.
     * @param accepterId The ID of the player accepting the request.
     * @return The created or updated party if successful, null otherwise.
     */
    fun acceptPartyRequest(requestId: UUID, acceptingGuildId: UUID, accepterId: UUID): Party?
    
    /**
     * Rejects a party request.
     *
     * @param requestId The ID of the party request to reject.
     * @param rejectingGuildId The ID of the guild rejecting the request.
     * @param rejecterId The ID of the player rejecting the request.
     * @return true if the rejection was successful, false otherwise.
     */
    fun rejectPartyRequest(requestId: UUID, rejectingGuildId: UUID, rejecterId: UUID): Boolean
    
    /**
     * Cancels a party request (by the requester).
     *
     * @param requestId The ID of the party request to cancel.
     * @param cancellingGuildId The ID of the guild cancelling the request.
     * @param cancellerId The ID of the player cancelling the request.
     * @return true if the cancellation was successful, false otherwise.
     */
    fun cancelPartyRequest(requestId: UUID, cancellingGuildId: UUID, cancellerId: UUID): Boolean
    
    /**
     * Invites another guild to an existing party.
     *
     * @param partyId The ID of the party.
     * @param inviterGuildId The ID of the guild doing the inviting.
     * @param targetGuildId The ID of the guild being invited.
     * @param inviterId The ID of the player making the invitation.
     * @return The party request if successful, null otherwise.
     */
    fun inviteToParty(partyId: UUID, inviterGuildId: UUID, targetGuildId: UUID, inviterId: UUID): PartyRequest?
    
    /**
     * Removes a guild from a party (leave party).
     *
     * @param partyId The ID of the party.
     * @param guildId The ID of the guild leaving.
     * @param actorId The ID of the player initiating the leave.
     * @return The updated party if successful, null if party was dissolved.
     */
    fun leaveParty(partyId: UUID, guildId: UUID, actorId: UUID): Party?
    
    /**
     * Dissolves a party completely.
     *
     * @param partyId The ID of the party to dissolve.
     * @param dissolverId The ID of the player dissolving the party.
     * @return true if the party was dissolved successfully, false otherwise.
     */
    fun dissolveParty(partyId: UUID, dissolverId: UUID): Boolean
    
    /**
     * Gets all active parties for a guild.
     *
     * @param guildId The ID of the guild.
     * @return A set of active parties the guild is part of.
     */
    fun getActivePartiesForGuild(guildId: UUID): Set<Party>
    
    /**
     * Gets the active party for a player (based on their guild membership).
     *
     * @param playerId The ID of the player.
     * @return The active party if the player's guild is in one, null otherwise.
     */
    fun getActivePartyForPlayer(playerId: UUID): Party?
    
    /**
     * Gets all pending party requests for a guild (incoming).
     *
     * @param guildId The ID of the guild.
     * @return A set of pending requests to the guild.
     */
    fun getPendingRequestsForGuild(guildId: UUID): Set<PartyRequest>
    
    /**
     * Gets all pending party requests from a guild (outgoing).
     *
     * @param guildId The ID of the guild.
     * @return A set of pending requests from the guild.
     */
    fun getPendingRequestsFromGuild(guildId: UUID): Set<PartyRequest>
    
    /**
     * Checks if a player has permission to manage parties for a guild.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @return true if the player can manage parties, false otherwise.
     */
    fun canManageParties(playerId: UUID, guildId: UUID): Boolean
    
    /**
     * Processes expired party requests and parties.
     * This should be called periodically for cleanup.
     *
     * @return The number of expired items processed.
     */
    fun processExpiredItems(): Int
    
    /**
     * Sets a party name.
     *
     * @param partyId The ID of the party.
     * @param name The new name for the party.
     * @param actorId The ID of the player setting the name.
     * @return true if successful, false otherwise.
     */
    fun setPartyName(partyId: UUID, name: String?, actorId: UUID): Boolean
    
    /**
     * Sets a party expiration time.
     *
     * @param partyId The ID of the party.
     * @param duration The duration until expiration.
     * @param actorId The ID of the player setting the expiration.
     * @return true if successful, false otherwise.
     */
    fun setPartyExpiration(partyId: UUID, duration: Duration, actorId: UUID): Boolean
    
    /**
     * Gets all online members of all guilds in a party.
     *
     * @param partyId The ID of the party.
     * @return A set of online player IDs in the party.
     */
    fun getOnlinePartyMembers(partyId: UUID): Set<UUID>

    /**
     * Sets role restrictions for a party.
     *
     * @param partyId The ID of the party.
     * @param restrictedRoles Set of rank IDs that can join (null for no restrictions).
     * @param actorId The ID of the player making the change.
     * @return true if successful, false otherwise.
     */
    fun setPartyRoleRestrictions(partyId: UUID, restrictedRoles: Set<UUID>?, actorId: UUID): Boolean

    /**
     * Checks if a player can join a party based on role restrictions.
     *
     * @param partyId The ID of the party.
     * @param playerId The ID of the player.
     * @return true if the player can join, false otherwise.
     */
    fun canPlayerJoinParty(partyId: UUID, playerId: UUID): Boolean

    /**
     * Gets a party by its ID.
     *
     * @param partyId The ID of the party to retrieve.
     * @return The party if found, null otherwise.
     */
    fun getParty(partyId: UUID): Party?

    /**
     * Updates party settings.
     *
     * @param partyId The ID of the party.
     * @param updates Map of properties to update.
     * @param actorId The ID of the player making the changes.
     * @return true if successful, false otherwise.
     */
    fun updatePartySettings(partyId: UUID, updates: Map<String, Any>, actorId: UUID): Boolean

    /**
     * Gets all members of a party (all players from all guilds in the party).
     *
     * @param partyId The ID of the party.
     * @return Set of player IDs in the party.
     */
    fun getPartyMembers(partyId: UUID): Set<UUID>

    /**
     * Gets party statistics.
     *
     * @param partyId The ID of the party.
     * @return Party statistics or null if party not found.
     */
    fun getPartyStatistics(partyId: UUID): PartyStatistics?

    /**
     * Gets the leader of a party.
     *
     * @param partyId The ID of the party.
     * @return The player ID of the party leader, or null if not found.
     */
    fun getPartyLeader(partyId: UUID): UUID?

    /**
     * Transfers party leadership.
     *
     * @param partyId The ID of the party.
     * @param newLeaderId The ID of the new leader.
     * @param actorId The ID of the current leader making the change.
     * @return true if successful, false otherwise.
     */
    fun transferPartyLeadership(partyId: UUID, newLeaderId: UUID, actorId: UUID): Boolean
}
