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
    internal data class Control(val name: Component, val action: () -> Unit)

    fun rows(stall: String): List<Control> = buildList {
        if (available("guildsales", stall)) {
            add(Control(lang.gui("community.stall.sales")) { run("guildsales", stall) })
        }
        if (available("stallaccess", stall)) {
            add(Control(Component.text("Stall flags and access")) { run("stallaccess", stall) })
        }
    }

    private fun available(command: String, stall: String): Boolean =
        stall.matches(Regex("[A-Za-z0-9_.:-]+")) && Bukkit.getCommandMap().getCommand(command) != null &&
            members.hasPermission(player.uniqueId, guild, RankPermission.EDIT_SHOP_STOCK)

    private fun run(command: String, stall: String) {
        if (available(command, stall)) {
            player.closeInventory()
            val arguments = if (command == "stallaccess") "settings $stall" else stall
            player.performCommand("$command $arguments")
        }
    }
}
