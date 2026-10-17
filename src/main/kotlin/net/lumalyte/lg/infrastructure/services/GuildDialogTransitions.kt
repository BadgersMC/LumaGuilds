package net.lumalyte.lg.infrastructure.services

import io.papermc.paper.registry.data.dialog.DialogBase
import org.bukkit.entity.Player
import java.util.WeakHashMap

/** Keep the current dialog visible until a guarded callback supplies its replacement. */
internal object GuildDialogTransitions {
    private val presentations = WeakHashMap<Player, Long>()

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

    private fun revision(player: Player): Long = synchronized(presentations) { presentations[player] ?: 0L }
}
