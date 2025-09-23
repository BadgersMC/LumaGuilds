package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import net.lumalyte.lg.interaction.menus.misc.ClaimListMenu
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@CommandAlias("claimmenu")
class ClaimMenuCommand : BaseCommand(), KoinComponent {

    private val menuFactory: MenuFactory by inject()

    @Default
    @CommandPermission("lumaguilds.command.claimmenu")
    fun onClaimMenu(player: Player) {
        val menuNavigator = MenuNavigator(player)
        menuNavigator.openMenu(menuFactory.createClaimListMenu(menuNavigator, player))
    }
}
