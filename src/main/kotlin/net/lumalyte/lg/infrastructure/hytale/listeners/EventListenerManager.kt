package net.lumalyte.lg.infrastructure.hytale.listeners

import com.hypixel.hytale.component.ComponentRegistryProxy
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.PartitionRepository
import org.slf4j.LoggerFactory

/**
 * Manages registration and lifecycle of all ECS event systems for LumaGuilds.
 *
 * This class centralizes EntityEventSystem registration using Hytale's
 * ComponentRegistryProxy to register systems with the ECS world.
 */
class EventListenerManager(
    private val entityStoreRegistry: ComponentRegistryProxy<EntityStore>,
    private val claimRepository: ClaimRepository,
    private val partitionRepository: PartitionRepository
) {
    private val log = LoggerFactory.getLogger(EventListenerManager::class.java)

    /**
     * Registers all EntityEventSystems with the Hytale ECS ComponentRegistry.
     * This should be called during plugin setup phase.
     */
    fun registerAllListeners() {
        log.info("Registering LumaGuilds ECS event systems...")

        // Register block protection systems
        registerBlockProtectionSystems()

        log.info("All ECS event systems registered successfully!")
    }

    /**
     * Registers EntityEventSystems for claim block protection.
     * These systems prevent unauthorized block modifications in claims.
     */
    private fun registerBlockProtectionSystems() {
        log.debug("Registering block protection ECS systems...")

        // Create and register PlaceBlockEvent system
        val placeBlockSystem = PlaceBlockProtectionSystem(
            claimRepository,
            partitionRepository
        )
        entityStoreRegistry.registerSystem(placeBlockSystem)

        // Create and register BreakBlockEvent system
        val breakBlockSystem = BreakBlockProtectionSystem(
            claimRepository,
            partitionRepository
        )
        entityStoreRegistry.registerSystem(breakBlockSystem)

        // Create and register UseBlockEvent.Pre system
        val useBlockSystem = UseBlockProtectionSystem(
            claimRepository,
            partitionRepository
        )
        entityStoreRegistry.registerSystem(useBlockSystem)

        log.debug("Block protection systems registered: PlaceBlock, BreakBlock, UseBlock")
    }
}
