package net.lumalyte.lg.infrastructure.hytale.adapters

import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import net.lumalyte.lg.domain.values.PlayerContext
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger("HytalePlayerAdapter")

/**
 * Converts Hytale PlayerRef to domain PlayerContext
 * Requires World to access the ECS Store
 */
fun PlayerRef.toPlayerContext(world: World): PlayerContext? {
    // Get entity reference
    val ref = this.reference ?: run {
        log.warn("PlayerRef for ${this.username} has no entity reference")
        return null
    }

    if (!ref.isValid()) {
        log.warn("PlayerRef for ${this.username} has invalid reference")
        return null
    }

    val store = world.entityStore.store

    // Get Player component
    val player = store.getComponent(ref, Player.getComponentType()) ?: run {
        log.warn("Could not get Player component for ${this.username}")
        return null
    }

    // Get UUID - handle nullable
    val playerUuid = player.uuid ?: run {
        log.warn("Player component for ${this.username} has no UUID")
        return null
    }

    // Collect permissions (basic implementation - could be expanded)
    val permissions = mutableSetOf<String>()
    // TODO: Query specific permissions as needed
    // For now, we'll check permissions on-demand via hasPermission()

    return PlayerContext(
        uuid = playerUuid,
        name = this.username,
        locale = this.language ?: "en_US",
        isOnline = true,
        permissions = permissions
    )
}

/**
 * Gets a player context by UUID from a world
 */
fun World.getPlayerContext(uuid: UUID): PlayerContext? {
    // Find PlayerRef by UUID
    val playerRef = this.playerRefs.find { it.uuid == uuid } ?: run {
        log.debug("Player $uuid not found in world ${this.name}")
        return null
    }

    return playerRef.toPlayerContext(this)
}

/**
 * Gets all online player contexts in a world
 */
fun World.getAllPlayerContexts(): List<PlayerContext> {
    return this.playerRefs.mapNotNull { it.toPlayerContext(this) }
}

/**
 * Checks if a player has a permission
 * This is a helper for when we only have UUID and need to check permission
 */
fun World.hasPermission(uuid: UUID, permission: String): Boolean {
    val playerRef = this.playerRefs.find { it.uuid == uuid } ?: return false
    val ref = playerRef.reference ?: return false
    if (!ref.isValid()) return false

    val store = this.entityStore.store
    val player = store.getComponent(ref, Player.getComponentType()) ?: return false

    return player.hasPermission(permission)
}
