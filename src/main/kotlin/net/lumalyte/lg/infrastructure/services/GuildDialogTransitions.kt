package net.lumalyte.lg.infrastructure.services

import io.papermc.paper.registry.data.dialog.DialogBase
import org.bukkit.entity.Player
import java.util.WeakHashMap

/** Keep the current dialog visible until a guarded callback supplies its replacement. */
internal object GuildDialogTransitions {
    private val presentations = WeakHashMap<Player, Long>()

    /** Inventory click handlers must finish before closing their container. */
    fun present(
        player: Player,
        valid: () -> Boolean,
        render: () -> Boolean,
        failed: () -> Unit,
        schedule: (() -> Unit) -> Unit = { task ->
            org.bukkit.Bukkit.getScheduler().runTask(net.lumalyte.lg.common.PluginKeys.getPlugin(), Runnable { task() })
        },
    ): Boolean {
        val epoch = GuildMenuLifecycle.generation(player)
        val token = epoch.get()
        // Cancelled navigation is handled; returning false would open a stale GUI fallback.
        if (!player.isOnline || !valid()) return true
        val sourceInventory = player.openInventory.topInventory
        if (sourceInventory.type in setOf(
                org.bukkit.event.inventory.InventoryType.CRAFTING,
                org.bukkit.event.inventory.InventoryType.PLAYER,
            )) return render()
        schedule {
            if (player.isOnline && epoch.get() == token &&
                player.openInventory.topInventory === sourceInventory && valid()) {
                renderQueued(player, render, failed)
            }
        }
        return true
    }

    @Suppress("TooGenericExceptionCaught") // Scheduled platform boundary must fail closed.
    private fun renderQueued(player: Player, render: () -> Boolean, failed: () -> Unit) {
        var success = false
        try {
            player.closeInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason.OPEN_NEW)
            success = render()
        } catch (error: Exception) {
            GuildMenuLifecycle.dismiss(player)
            org.slf4j.LoggerFactory.getLogger(javaClass).warn("Guild dialog handoff failed", error)
        }
        if (!success) failed()
    }

    /** Paper rejects its default pause=true when afterAction is NONE. */
    fun configure(builder: DialogBase.Builder): DialogBase.Builder =
        builder.pause(false).afterAction(DialogBase.DialogAfterAction.NONE)

    fun shown(player: Player) {
        synchronized(presentations) {
            presentations[player] = (presentations[player] ?: 0L) + 1L
        }
    }

    fun dispatch(
        player: Player,
        action: () -> Unit,
    ) {
        val before = revision(player)
        action()
        // Inventory/terminal actions need an explicit dismissal with afterAction NONE.
        // A replacement dialog must never be cleared by its predecessor's callback.
        if (revision(player) == before) player.closeDialog()
    }

    fun revision(player: Player): Long = synchronized(presentations) { presentations[player] ?: 0L }
}
