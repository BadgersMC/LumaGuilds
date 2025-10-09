package net.lumalyte.lg.interaction.menus.management

import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.values.LocalizationKeys
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.common.ConfirmationMenu
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class ClaimTransferMenu(private val menuNavigator: MenuNavigator, private val claim: Claim?,
                        private val player: Player, private val messageService: MessageService): Menu, KoinComponent {
    private val localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider by inject()
    private val menuFactory: MenuFactory by inject()

    override fun open() {
        if (claim == null) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Error: No claim available")
            return
        }

        val playerId = player.uniqueId
        val confirmAction: () -> Unit = {
            menuNavigator.openMenu(menuFactory.createClaimTransferNamingMenu(menuNavigator, claim, player))
        }
        menuNavigator.openMenu(menuFactory.createConfirmationMenu(menuNavigator, player,
            localizationProvider.get(playerId, LocalizationKeys.MENU_TRANSFER_TITLE), messageService, confirmAction))
    }
}
