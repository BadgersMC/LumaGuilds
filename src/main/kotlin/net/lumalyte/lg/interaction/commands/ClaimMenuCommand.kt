package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import net.lumalyte.lg.interaction.menus.misc.ClaimListMenu

@CommandAlias("claimmenu")
class ClaimMenuCommand(
    menuFactory: Lazy<MenuFactory>,
) : BaseCommand() {

    private val menuFactory: MenuFactory by menuFactory

    @Default
    @CommandPermission("lumaguilds.command.claimmenu")
    fun onClaimMenu(player: Player) {
        val menuNavigator = MenuNavigator(player)
        menuNavigator.openMenu(menuFactory.createClaimListMenu(menuNavigator, player))
    }
}
