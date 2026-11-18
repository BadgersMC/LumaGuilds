package net.lumalyte.lg.infrastructure.services.hall

import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityCombustEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.util.concurrent.atomic.AtomicBoolean

/**
 * REQ-292: Hall statues can't be hurt, killed, set alight or handled. Being invulnerable doesn't stop a
 * creative-mode player, so every damage event on a marked statue entity is cancelled first.
 */
internal object HallStatueProtection : Listener {
    val KEY = NamespacedKey("lumaguilds", "hall_statue")
    private val registered = AtomicBoolean(false)

    fun register(plugin: Plugin) {
        if (registered.compareAndSet(false, true)) plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /** Marks [entity] as part of a Hall statue. */
    fun mark(entity: Entity) = entity.persistentDataContainer.set(KEY, PersistentDataType.BYTE, 1)

    fun isStatue(entity: Entity): Boolean = entity.persistentDataContainer.has(KEY, PersistentDataType.BYTE)

    @EventHandler(priority = EventPriority.LOWEST)
    fun damage(event: EntityDamageEvent) {
        if (isStatue(event.entity)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun combust(event: EntityCombustEvent) {
        if (isStatue(event.entity)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun interact(event: PlayerInteractEntityEvent) {
        if (isStatue(event.rightClicked)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun interactAt(event: PlayerInteractAtEntityEvent) {
        if (isStatue(event.rightClicked)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun manipulate(event: PlayerArmorStandManipulateEvent) {
        if (isStatue(event.rightClicked)) event.isCancelled = true
    }
}
