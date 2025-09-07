package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player

class GuildLeaveConfirmationMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                                private var guild: Guild): Menu {

    override fun open() {
        player.sendMessage("§eLeave guild confirmation menu coming soon!")
        menuNavigator.openMenu(GuildControlPanelMenu(menuNavigator, player, guild))
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
