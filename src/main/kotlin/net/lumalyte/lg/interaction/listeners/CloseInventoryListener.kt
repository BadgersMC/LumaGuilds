package net.lumalyte.lg.interaction.listeners

import net.lumalyte.lg.application.actions.player.UnregisterClaimMenuOpening
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent

class CloseInventoryListener(
    unregisterClaimMenuOpening: Lazy<UnregisterClaimMenuOpening>,
) : Listener {
    private val unregisterClaimMenuOpening: UnregisterClaimMenuOpening by unregisterClaimMenuOpening

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.player !is Player) return
        if (event.reason == InventoryCloseEvent.Reason.OPEN_NEW) return
        if (event.reason == InventoryCloseEvent.Reason.UNKNOWN) return
        net.lumalyte.lg.infrastructure.services.GuildMenuLifecycle.inventoryClosed(event.player as Player, event.reason)
        unregisterClaimMenuOpening.execute(event.player.uniqueId)
    }
}
