package net.lumalyte.lg.interaction.menus.management

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.guiTitle

import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.common.ConfirmationMenu
import org.bukkit.entity.Player
import net.lumalyte.lg.interaction.menus.MenuServices
import kotlin.getValue

class ClaimTransferMenu(private val menuNavigator: MenuNavigator, private val claim: Claim?,
                        private val player: Player, private val services: MenuServices): Menu {
    private val lang: LangService by services.langService
    private val menuFactory: MenuFactory by services.menuFactory

    override fun open() {
        if (claim == null) {
            player.sendMessage(lang.msg("menu.common.feedback.no_claim"))
            return
        }

        val playerId = player.uniqueId
        val confirmAction: () -> Unit = {
            menuNavigator.openMenu(menuFactory.createClaimTransferNamingMenu(menuNavigator, claim, player))
        }
        menuNavigator.openMenu(menuFactory.createConfirmationMenu(menuNavigator, player,
            lang.guiTitle("menu.transfer.title"), confirmAction))
    }
}
