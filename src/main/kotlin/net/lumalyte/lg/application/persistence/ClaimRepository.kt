package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.values.Position3D
import java.util.*

/**
 * A repository that handles the persistence of Claims.
 */
interface ClaimRepository {
    /**
     * Gets all claims that exist.
     *
     * @return The set of all claims.
     */
    fun getAll(): Set<Claim>

    /**
     * Gets a claim by its id.
     *
     * @param id the unique id of the claim.
     * @return The found claim, or null if it doesn't exist.
     */
    fun getById(id: UUID): Claim?

    /**
     * Gets all claims that a player owns.
     *
     * @param playerId The id of the player to retrieve claims for.
     * @return A set of claims owned by the player.
     */
    fun getByPlayer(playerId: UUID): Set<Claim>

    /**
     * Gets all claims that a team/guild owns.
     *
     * @param teamId The id of the team/guild to retrieve claims for.
     * @return A set of claims owned by the team/guild.
     */
    fun getByTeam(teamId: UUID): Set<Claim>

    /**
     * Gets the claim owned by the player by name.
     *
     * @param playerId The id of the player to retrieve claims for.
     * @param name The name of the target claim.
     * @return The found claim, or null if it doesn't exist.
     */
    fun getByName(playerId: UUID, name: String): Claim?

    /**
     * Gets the claim owned by a team by name.
     *
     * @param teamId The id of the team to retrieve claims for.
     * @param name The name of the target claim.
     * @return The found claim, or null if it doesn't exist.
     */
    fun getByNameForTeam(teamId: UUID, name: String): Claim?

    /**
     * Retrieves a claim by the position in the world.
     *
     * @param position The position in the world.
     * @param worldId The unique id of the world.
     * @return The found claim, or null if it doesn't exist.
     */
    fun getByPosition(position: Position3D, worldId: UUID): Claim?

    /**
     * Adds a new claim.
     *
     * @param claim The claim to add.
     */
    fun add(claim: Claim): Boolean

    /**
     * Updates the data of an existing claim.
     *
     * @param claim The claim to update.
     */
    fun update(claim: Claim): Boolean

    /**
     * Removes an existing claim.
     *
     * @param claimId The id of the claim to remove.
     */
    fun remove(claimId: UUID): Boolean
}
