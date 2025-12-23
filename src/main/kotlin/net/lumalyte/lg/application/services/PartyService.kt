package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.PartyRequest
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
     * @param suppressBroadcast If true, suppresses the party creation broadcast message. Defaults to true.
     * @return The created party if successful, null otherwise.
     */
    fun createParty(party: Party, suppressBroadcast: Boolean = true): Party?

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
     * Kicks a player from a party/channel by removing their guild if it's a single-guild party,
     * or preventing them from chatting if it's a multi-guild party.
     *
     * @param partyId The ID of the party.
     * @param targetPlayerId The ID of the player to kick.
     * @param actorId The ID of the player performing the kick.
     * @return The updated party if successful, null otherwise.
     */
    fun kickPlayer(partyId: UUID, targetPlayerId: UUID, actorId: UUID): Party?

    /**
     * Mutes a player in a party/channel.
     *
     * @param partyId The ID of the party.
     * @param targetPlayerId The ID of the player to mute.
     * @param actorId The ID of the player performing the mute.
     * @param duration The duration of the mute (null for permanent).
     * @return The updated party if successful, null otherwise.
     */
    fun mutePlayer(partyId: UUID, targetPlayerId: UUID, actorId: UUID, duration: Duration?): Party?

    /**
     * Unmutes a player in a party/channel.
     *
     * @param partyId The ID of the party.
     * @param targetPlayerId The ID of the player to unmute.
     * @param actorId The ID of the player performing the unmute.
     * @return The updated party if successful, null otherwise.
     */
    fun unmutePlayer(partyId: UUID, targetPlayerId: UUID, actorId: UUID): Party?

    /**
     * Bans a player from a party/channel.
     *
     * @param partyId The ID of the party.
     * @param targetPlayerId The ID of the player to ban.
     * @param actorId The ID of the player performing the ban.
     * @return The updated party if successful, null otherwise.
     */
    fun banPlayer(partyId: UUID, targetPlayerId: UUID, actorId: UUID): Party?

    /**
     * Unbans a player from a party/channel.
     *
     * @param partyId The ID of the party.
     * @param targetPlayerId The ID of the player to unban.
     * @param actorId The ID of the player performing the unban.
     * @return The updated party if successful, null otherwise.
     */
    fun unbanPlayer(partyId: UUID, targetPlayerId: UUID, actorId: UUID): Party?

    /**
     * Gets all parties/channels for a specific guild (used to find default guild channels).
     *
     * @param guildId The ID of the guild.
     * @return A set of all parties for the guild (including single-guild channels).
     */
    fun getAllPartiesForGuild(guildId: UUID): Set<Party>
}
