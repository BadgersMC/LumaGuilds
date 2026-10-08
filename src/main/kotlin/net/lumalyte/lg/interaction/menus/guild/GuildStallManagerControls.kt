package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.i18n.gui
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

/** Optional companion actions share the existing manager permission and recheck it on each click. */
internal class GuildStallManagerControls(
    private val player: Player,
    private val guild: UUID,
    private val members: MemberService,
    private val lang: LangService,
) {
    /** Presentation and an authority-rechecking click callback for either client menu. */
    internal data class Control(val name: Component, val action: () -> Unit)

    /** Current permitted companion commands, omitted when unavailable. */
    fun rows(stall: String): List<Control> {
        return buildList {
            if (available(SALES, stall)) {
                add(Control(lang.gui("community.stall.sales")) { run(SALES, stall) })
            }
            if (available(ACCESS, stall)) {
                add(Control(lang.gui("community.stall.access")) { run(ACCESS, stall) })
            }
        }
    }

    private fun available(command: String, stall: String): Boolean =
        stall.matches(Regex("[A-Za-z0-9_.:-]+")) && Bukkit.getCommandMap().getCommand(command) != null &&
            members.hasPermission(player.uniqueId, guild, RankPermission.EDIT_SHOP_STOCK)

    private fun run(command: String, stall: String) {
        if (available(command, stall)) {
            player.closeInventory()
            val arguments = if (command == ACCESS) "settings $stall" else stall
            player.performCommand("$command $arguments")
        }
    }

    private companion object {
        const val SALES = "guildsales"
        const val ACCESS = "stallaccess"
    }
}
