package net.lumalyte.lg.infrastructure.services

import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryCloseEvent
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong

/** Shared navigation epoch for inventory, dialog and delayed presentation work. */
internal object GuildMenuLifecycle {
    private val generations = WeakHashMap<Player, AtomicLong>()

    fun generation(player: Player): AtomicLong = synchronized(generations) {
        generations.getOrPut(player) { AtomicLong() }
    }

    fun invalidate(player: Player) {
        generation(player).incrementAndGet()
    }

    fun inventoryClosed(player: Player, reason: InventoryCloseEvent.Reason) {
        // Plugin closes also hand off to chat inputs and must preserve their navigation token.
        if (reason == InventoryCloseEvent.Reason.PLAYER) invalidate(player)
    }

    fun dismiss(player: Player) {
        invalidate(player)
        player.closeDialog()
        player.closeInventory()
    }
}
