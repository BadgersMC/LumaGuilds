package net.lumalyte.lg.interaction.menus.management

import net.badgersmc.nexus.i18n.LangService

import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.common.ConfirmationMenu
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

class ClaimTransferMenu(private val menuNavigator: MenuNavigator, private val claim: Claim?,
                        private val player: Player): Menu, KoinComponent {
    private val lang: LangService by inject()
    private val menuFactory: MenuFactory by inject()

    override fun open() {
        if (claim == null) {
            player.sendMessage("§cError: No claim available")
            return
        }

        val playerId = player.uniqueId
        val confirmAction: () -> Unit = {
            menuNavigator.openMenu(menuFactory.createClaimTransferNamingMenu(menuNavigator, claim, player))
        }
        menuNavigator.openMenu(menuFactory.createConfirmationMenu(menuNavigator, player,
            lang.legacy("menu.transfer.title"), confirmAction))
    }
}
