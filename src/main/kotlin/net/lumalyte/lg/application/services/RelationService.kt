package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.Relation
import net.lumalyte.lg.domain.entities.RelationType
import java.time.Duration
import java.util.UUID

/**
 * Service interface for managing guild relations and diplomatic flows.
 */
interface RelationService {
    
    /**
     * Requests an alliance between two guilds.
     * Creates a pending alliance request that must be accepted by the target guild.
     *
     * @param requestingGuildId The ID of the guild making the request.
     * @param targetGuildId The ID of the guild to ally with.
     * @param actorId The ID of the player making the request.
     * @return The created relation if successful, null if the request failed.
     */
    fun requestAlliance(requestingGuildId: UUID, targetGuildId: UUID, actorId: UUID): Relation?
    
    /**
     * Accepts a pending alliance request.
     *
     * @param relationId The ID of the pending alliance relation.
     * @param acceptingGuildId The ID of the guild accepting the request.
     * @param actorId The ID of the player accepting the request.
     * @return The updated relation if successful, null if the acceptance failed.
     */
    fun acceptAlliance(relationId: UUID, acceptingGuildId: UUID, actorId: UUID): Relation?
    
    /**
     * Declares war (enemy status) between two guilds.
     * This immediately sets both guilds as enemies without requiring mutual consent.
     *
     * @param declaringGuildId The ID of the guild declaring war.
     * @param targetGuildId The ID of the guild to declare war on.
     * @param actorId The ID of the player declaring war.
     * @return The created relation if successful, null if the declaration failed.
     */
    fun declareWar(declaringGuildId: UUID, targetGuildId: UUID, actorId: UUID): Relation?
    
    /**
     * Requests a truce between two guilds.
     * Creates a pending truce request that must be accepted by the target guild.
     *
     * @param requestingGuildId The ID of the guild requesting the truce.
     * @param targetGuildId The ID of the guild to make peace with.
     * @param actorId The ID of the player making the request.
     * @param duration The duration of the truce.
     * @return The created relation if successful, null if the request failed.
     */
    fun requestTruce(requestingGuildId: UUID, targetGuildId: UUID, actorId: UUID, duration: Duration): Relation?
    
    /**
     * Accepts a pending truce request.
     *
     * @param relationId The ID of the pending truce relation.
     * @param acceptingGuildId The ID of the guild accepting the truce.
     * @param actorId The ID of the player accepting the request.
     * @return The updated relation if successful, null if the acceptance failed.
     */
    fun acceptTruce(relationId: UUID, acceptingGuildId: UUID, actorId: UUID): Relation?
    
    /**
     * Requests to end hostilities and return to neutral status.
     * Creates a pending unenemy request that must be accepted by the target guild.
     *
     * @param requestingGuildId The ID of the guild requesting peace.
     * @param targetGuildId The ID of the guild to make peace with.
     * @param actorId The ID of the player making the request.
     * @return The created relation if successful, null if the request failed.
     */
    fun requestUnenemy(requestingGuildId: UUID, targetGuildId: UUID, actorId: UUID): Relation?
    
    /**
     * Accepts a pending unenemy request, setting relation to neutral.
     *
     * @param relationId The ID of the pending unenemy relation.
     * @param acceptingGuildId The ID of the guild accepting the request.
     * @param actorId The ID of the player accepting the request.
     * @return The updated relation if successful, null if the acceptance failed.
     */
    fun acceptUnenemy(relationId: UUID, acceptingGuildId: UUID, actorId: UUID): Relation?
    
    /**
     * Rejects a pending relation request.
     *
     * @param relationId The ID of the pending relation.
     * @param rejectingGuildId The ID of the guild rejecting the request.
     * @param actorId The ID of the player rejecting the request.
     * @return true if the rejection was successful, false otherwise.
     */
    fun rejectRequest(relationId: UUID, rejectingGuildId: UUID, actorId: UUID): Boolean
    
    /**
     * Cancels a pending request made by the requesting guild.
     *
     * @param relationId The ID of the pending relation.
     * @param cancellingGuildId The ID of the guild cancelling the request.
     * @param actorId The ID of the player cancelling the request.
     * @return true if the cancellation was successful, false otherwise.
     */
    fun cancelRequest(relationId: UUID, cancellingGuildId: UUID, actorId: UUID): Boolean
    
    /**
     * Gets the current relation between two guilds.
     *
     * @param guildA The first guild ID.
     * @param guildB The second guild ID.
     * @return The relation if one exists, null otherwise.
     */
    fun getRelation(guildA: UUID, guildB: UUID): Relation?
    
    /**
     * Gets the effective relation type between two guilds.
     * Returns NEUTRAL if no relation exists or if the relation has expired.
     *
     * @param guildA The first guild ID.
     * @param guildB The second guild ID.
     * @return The effective relation type.
     */
    fun getRelationType(guildA: UUID, guildB: UUID): RelationType
    
    /**
     * Gets all relations for a guild.
     *
     * @param guildId The ID of the guild.
     * @return A set of relations involving the guild.
     */
    fun getGuildRelations(guildId: UUID): Set<Relation>
    
    /**
     * Gets relations for a guild filtered by type.
     *
     * @param guildId The ID of the guild.
     * @param type The relation type to filter by.
     * @return A set of relations of the specified type.
     */
    fun getGuildRelationsByType(guildId: UUID, type: RelationType): Set<Relation>
    
    /**
     * Gets all pending requests for a guild (both sent and received).
     *
     * @param guildId The ID of the guild.
     * @return A set of pending relation requests.
     */
    fun getPendingRequests(guildId: UUID): Set<Relation>
    
    /**
     * Gets all pending requests received by a guild.
     *
     * @param guildId The ID of the guild.
     * @return A set of pending requests that the guild can accept or reject.
     */
    fun getIncomingRequests(guildId: UUID): Set<Relation>
    
    /**
     * Gets all pending requests sent by a guild.
     *
     * @param guildId The ID of the guild.
     * @return A set of pending requests that the guild has made.
     */
    fun getOutgoingRequests(guildId: UUID): Set<Relation>
    
    /**
     * Checks if a player has permission to manage relations for a guild.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @return true if the player can manage relations, false otherwise.
     */
    fun canManageRelations(playerId: UUID, guildId: UUID): Boolean
    
    /**
     * Processes expired relations and updates their status.
     * This should be called periodically to handle truce expirations.
     *
     * @return The number of relations that were updated.
     */
    fun processExpiredRelations(): Int
    
    /**
     * Validates if a relation change is allowed based on current state and guild rules.
     *
     * @param fromGuildId The guild initiating the relation change.
     * @param toGuildId The target guild for the relation change.
     * @param newType The desired new relation type.
     * @return true if the relation change is valid, false otherwise.
     */
    fun isValidRelationChange(fromGuildId: UUID, toGuildId: UUID, newType: RelationType): Boolean

    /**
     * Checks if a guild is currently in an active war.
     *
     * @param guildId The ID of the guild.
     * @return true if the guild is in an active war, false otherwise.
     */
    fun isGuildInActiveWar(guildId: UUID): Boolean
}
