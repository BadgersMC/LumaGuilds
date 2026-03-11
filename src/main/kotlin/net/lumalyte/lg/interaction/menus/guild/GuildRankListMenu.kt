package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player

class GuildRankListMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                       private var guild: Guild): Menu {

    override fun open() {
        player.sendMessage("§eRank List menu coming soon!")
        menuNavigator.goBack()
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
