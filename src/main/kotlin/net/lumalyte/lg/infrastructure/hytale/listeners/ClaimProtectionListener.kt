package net.lumalyte.lg.infrastructure.hytale.listeners

import com.hypixel.hytale.component.Archetype
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.ExactArchetypeQuery
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.PartitionRepository
import net.lumalyte.lg.domain.values.Position
import net.lumalyte.lg.infrastructure.hytale.sounds.ClaimSounds

/**
 * EntityEventSystem that protects claimed areas from block placement by non-owners.
 * Listens to PlaceBlockEvent and cancels unauthorized placements.
 */
class PlaceBlockProtectionSystem(
    private val claimRepository: ClaimRepository,
    private val partitionRepository: PartitionRepository
) : EntityEventSystem<EntityStore, PlaceBlockEvent>(PlaceBlockEvent::class.java) {

    override fun getQuery(): Query<EntityStore> {
        // Handle all PlaceBlockEvent events
        return ExactArchetypeQuery(Archetype.empty())
    }

    override fun handle(
        entityId: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        event: PlaceBlockEvent
    ) {
        // Get the PlayerRef component from the entity that triggered the event
        val playerRef = chunk.getComponent(entityId, PlayerRef.getComponentType()) ?: return
        val playerId = playerRef.getUuid()

        // Get the block position where the player is trying to place
        val targetBlock = event.getTargetBlock()
        val blockPos = Position(targetBlock.x, targetBlock.y, targetBlock.z)

        // Check if this position is within any partition
        val partitions = partitionRepository.getByPosition(blockPos)
        if (partitions.isEmpty()) {
            // No claim at this location, allow placement
            return
        }

        // Get the claim for the first partition found
        val partition = partitions.first()
        val claim = claimRepository.getById(partition.claimId) ?: return

        // Check if the player has permission to build in the claim
        if (!claim.canPlayerBuild(playerId)) {
            // Player is not the owner or trusted, cancel the event
            event.setCancelled(true)

            // Play violation sound at the block location
            ClaimSounds.playClaimViolation(
                playerRef,
                targetBlock.x.toFloat(),
                targetBlock.y.toFloat(),
                targetBlock.z.toFloat()
            )

            // Send message to player
            playerRef.sendMessage(Message.raw("You cannot place blocks in this claim!").color("red"))
        }
    }
}

/**
 * EntityEventSystem that protects claimed areas from block breaking by non-owners.
 * Listens to BreakBlockEvent and cancels unauthorized breaking.
 */
class BreakBlockProtectionSystem(
    private val claimRepository: ClaimRepository,
    private val partitionRepository: PartitionRepository
) : EntityEventSystem<EntityStore, BreakBlockEvent>(BreakBlockEvent::class.java) {

    override fun getQuery(): Query<EntityStore> {
        // Handle all BreakBlockEvent events
        return ExactArchetypeQuery(Archetype.empty())
    }

    override fun handle(
        entityId: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        event: BreakBlockEvent
    ) {
        // Get the PlayerRef component from the entity that triggered the event
        val playerRef = chunk.getComponent(entityId, PlayerRef.getComponentType()) ?: return
        val playerId = playerRef.getUuid()

        // Get the block position where the player is trying to break
        val targetBlock = event.getTargetBlock()
        val blockPos = Position(targetBlock.x, targetBlock.y, targetBlock.z)

        // Check if this position is within any partition
        val partitions = partitionRepository.getByPosition(blockPos)
        if (partitions.isEmpty()) {
            // No claim at this location, allow breaking
            return
        }

        // Get the claim for the first partition found
        val partition = partitions.first()
        val claim = claimRepository.getById(partition.claimId) ?: return

        // Check if the player has permission to build in the claim
        if (!claim.canPlayerBuild(playerId)) {
            // Player is not the owner or trusted, cancel the event
            event.setCancelled(true)

            // Play violation sound at the block location
            ClaimSounds.playClaimViolation(
                playerRef,
                targetBlock.x.toFloat(),
                targetBlock.y.toFloat(),
                targetBlock.z.toFloat()
            )

            // Send message to player
            playerRef.sendMessage(Message.raw("You cannot break blocks in this claim!").color("red"))
        }
    }
}

/**
 * EntityEventSystem that protects claimed areas from block interaction by non-owners.
 * Listens to UseBlockEvent.Pre and cancels unauthorized interactions.
 */
class UseBlockProtectionSystem(
    private val claimRepository: ClaimRepository,
    private val partitionRepository: PartitionRepository
) : EntityEventSystem<EntityStore, UseBlockEvent.Pre>(UseBlockEvent.Pre::class.java) {

    override fun getQuery(): Query<EntityStore> {
        // Handle all UseBlockEvent.Pre events
        return ExactArchetypeQuery(Archetype.empty())
    }

    override fun handle(
        entityId: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        event: UseBlockEvent.Pre
    ) {
        // Get the PlayerRef component from the entity that triggered the event
        val playerRef = chunk.getComponent(entityId, PlayerRef.getComponentType()) ?: return
        val playerId = playerRef.getUuid()

        // Get the block position where the player is trying to interact
        val targetBlock = event.getTargetBlock()
        val blockPos = Position(targetBlock.x, targetBlock.y, targetBlock.z)

        // Check if this position is within any partition
        val partitions = partitionRepository.getByPosition(blockPos)
        if (partitions.isEmpty()) {
            // No claim at this location, allow interaction
            return
        }

        // Get the claim for the first partition found
        val partition = partitions.first()
        val claim = claimRepository.getById(partition.claimId) ?: return

        // Check if the player has permission to build in the claim
        if (!claim.canPlayerBuild(playerId)) {
            // Player is not the owner or trusted, cancel the event
            event.setCancelled(true)

            // Play violation sound at the block location
            ClaimSounds.playClaimViolation(
                playerRef,
                targetBlock.x.toFloat(),
                targetBlock.y.toFloat(),
                targetBlock.z.toFloat()
            )

            // Send message to player
            playerRef.sendMessage(Message.raw("You cannot interact with blocks in this claim!").color("red"))
        }
    }
}
