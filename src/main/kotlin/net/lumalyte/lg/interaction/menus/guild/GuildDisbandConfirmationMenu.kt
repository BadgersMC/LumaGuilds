package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.MenuFactory
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GuildDisbandConfirmationMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                                  private var guild: Guild): Menu, KoinComponent {

    private val menuFactory: MenuFactory by inject()

    override fun open() {
        player.sendMessage("§eGuild disband confirmation menu coming soon!")
        menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
