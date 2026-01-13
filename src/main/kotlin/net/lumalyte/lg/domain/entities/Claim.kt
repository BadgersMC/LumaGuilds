package net.lumalyte.lg.domain.entities

import net.lumalyte.lg.domain.values.Position3D
import java.time.Instant
import java.util.UUID
import kotlin.concurrent.thread

/**
 * A claim object holds the data for the world its in and the players associated with it. It relies on partitions to
 * define its shape.
 *
 * @constructor Compiles an existing claim using its complete data set.
 * @property id The unique identifier for the claim.
 * @property worldId the unique identifier for the world.
 * @property playerId The unique identifier of the owning player.
 * @property teamId The unique identifier of the owning team/guild, if any.
 * @property creationTime The timestamp when the claim was created.
 * @property name The name of the claim.
 * @property description The description of the claim.
 * @property position The position in the world the claim exists in.
 * @property icon The name of the material the claim is using as an icon.
 */
data class Claim(var id: UUID, var worldId: UUID, var playerId: UUID, var teamId: UUID?, val creationTime: Instant,
            val name: String, val description: String, val position: Position3D, val icon: String) {
    init {
        require(name.length in 1..50) { "Name must be between 1 and 50 characters." }
        require(description.length <= 300) { "Description cannot exceed 300 characters." }
    }

    var breakCount = 3

    private val defaultBreakCount = 3
    private var breakPeriod = false

    // Key: UUID of player which transfer request is sent to
    // Value: expiry time of the transfer request (Default request timestamp + 5 minutes)
    var transferRequests: java.util.HashMap<UUID, Int> = HashMap()

    /**
     * Compiles a new claim based on the minimum details required.
     *
     * @param worldId The unique identifier of the world the claim is to be made in.
     * @param playerId The id of the owning player.
     * @param teamId The id of the owning team/guild, if any.
     * @param position The position of the claim.
     * @param name The name of the claim.
     */
    constructor(worldId: UUID, playerId: UUID, teamId: UUID? = null, position: Position3D, name: String) : this(
        UUID.randomUUID(), worldId, playerId, teamId, Instant.now(), name, "", position, "BELL")

    /**
     * Marks that a break count reset is in progress.
     * The actual scheduling logic should be handled in the infrastructure layer.
     */
    fun startBreakPeriod() {
        breakPeriod = true
    }

    /**
     * Resets the break count to default.
     */
    fun resetBreakCount() {
        breakCount = defaultBreakCount
        breakPeriod = false
    }
}
