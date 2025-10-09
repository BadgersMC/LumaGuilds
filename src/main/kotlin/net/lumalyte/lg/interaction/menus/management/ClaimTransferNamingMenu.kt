package net.lumalyte.lg.interaction.menus.management

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.AnvilGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.actions.claim.transfer.AcceptTransferRequest
import net.lumalyte.lg.application.actions.player.IsPlayerInClaimMenu
import net.lumalyte.lg.application.results.claim.transfer.AcceptTransferRequestResult
import net.lumalyte.lg.application.results.player.IsPlayerInClaimMenuResult
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.values.LocalizationKeys
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class ClaimTransferNamingMenu(private val menuNavigator: MenuNavigator, private val claim: Claim?,
                              private val player: Player, private val messageService: MessageService): Menu, KoinComponent {
    private val localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider by inject()
    private val acceptTransferRequest: AcceptTransferRequest by inject()
    private val isPlayerInClaimMenu: IsPlayerInClaimMenu by inject()

    var name = ""
    var previousResult: AcceptTransferRequestResult? = null

    override fun open() {
        if (claim == null) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Error: No claim available")
            return
        }

        // Create transfer naming menu
        val playerId = player.uniqueId
        val gui = AnvilGui(localizationProvider.get(playerId, LocalizationKeys.MENU_NAMING_TITLE))
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent -> if (guiEvent.click == ClickType.SHIFT_LEFT ||
            guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true }

        // Add lodestone menu item
        val firstPane = StaticPane(0, 0, 1, 1)
        val lodestoneItem = ItemStack(Material.BELL)
            .name(name)
            .addAdventureLore(player, messageService, "${claim.position.x}, ${claim.position.y}, ${claim.position.z}")
        val guiItem = GuiItem(lodestoneItem) { guiEvent -> guiEvent.isCancelled = true }
        firstPane.addItem(guiItem, 0, 0)
        gui.firstItemComponent.addPane(firstPane)

        // Add message menu item if name is already taken
        val secondPane = StaticPane(0, 0, 1, 1)
        gui.secondItemComponent.addPane(secondPane)
        when (previousResult) {
            AcceptTransferRequestResult.NoActiveTransferRequest -> {
                val paperItem = ItemStack(Material.MAGMA_CREAM)
                    .name(localizationProvider.get(playerId,
                        LocalizationKeys.ACCEPT_TRANSFER_CONDITION_INVALID_REQUEST))
                val guiPaperItem = GuiItem(paperItem) { guiEvent -> guiEvent.isCancelled = true }
                secondPane.addItem(guiPaperItem, 0, 0)
            }
            AcceptTransferRequestResult.ClaimNotFound -> {
                val paperItem = ItemStack(Material.MAGMA_CREAM)
                    .name(localizationProvider.get(playerId, LocalizationKeys.ACCEPT_TRANSFER_CONDITION_INVALID_CLAIM))
                val guiPaperItem = GuiItem(paperItem) { guiEvent -> guiEvent.isCancelled = true }
                secondPane.addItem(guiPaperItem, 0, 0)
            }
            AcceptTransferRequestResult.BlockLimitExceeded -> {
                val paperItem = ItemStack(Material.MAGMA_CREAM)
                    .name(localizationProvider.get(playerId, LocalizationKeys.CREATION_CONDITION_BLOCKS))
                val guiPaperItem = GuiItem(paperItem) { guiEvent -> guiEvent.isCancelled = true }
                secondPane.addItem(guiPaperItem, 0, 0)
            }
            AcceptTransferRequestResult.ClaimLimitExceeded -> {
                val paperItem = ItemStack(Material.MAGMA_CREAM)
                    .name(localizationProvider.get(playerId, LocalizationKeys.CREATION_CONDITION_CLAIMS))
                val guiPaperItem = GuiItem(paperItem) { guiEvent -> guiEvent.isCancelled = true }
                secondPane.addItem(guiPaperItem, 0, 0)
            }
            AcceptTransferRequestResult.NameAlreadyExists -> {
                val paperItem = ItemStack(Material.PAPER)
                    .name(localizationProvider.get(playerId, LocalizationKeys.CREATION_CONDITION_EXISTING))
                val guiPaperItem = GuiItem(paperItem) { guiEvent -> guiEvent.isCancelled = true }
                secondPane.addItem(guiPaperItem, 0, 0)
            }
            AcceptTransferRequestResult.PlayerOwnsClaim -> {
                val paperItem = ItemStack(Material.MAGMA_CREAM)
                    .name(localizationProvider.get(playerId, LocalizationKeys.ACCEPT_TRANSFER_CONDITION_OWNER))
                val guiPaperItem = GuiItem(paperItem) { guiEvent -> guiEvent.isCancelled = true }
                secondPane.addItem(guiPaperItem, 0, 0)
            }
            AcceptTransferRequestResult.StorageError -> {
                val paperItem = ItemStack(Material.MAGMA_CREAM)
                    .name(localizationProvider.get(playerId, LocalizationKeys.GENERAL_ERROR))
                val guiPaperItem = GuiItem(paperItem) { guiEvent -> guiEvent.isCancelled = true }
                secondPane.addItem(guiPaperItem, 0, 0)
            }
            else -> {}
        }

        // Add confirm menu item.
        val thirdPane = StaticPane(0, 0, 1, 1)
        val confirmItem = ItemStack(Material.NETHER_STAR)
            .name(localizationProvider.get(playerId, LocalizationKeys.MENU_COMMON_ITEM_CONFIRM_NAME))
        val confirmGuiItem = GuiItem(confirmItem) { guiEvent ->
            val previousOwnerId = claim.playerId

            previousResult = acceptTransferRequest.execute(claim.id, player.uniqueId, gui.renameText)
            when (previousResult) {
                AcceptTransferRequestResult.Success -> {
                    // Close previous owner's inventory if they were in it
                    val claimMenuResult = isPlayerInClaimMenu.execute(player.uniqueId, claim.id)
                    when (claimMenuResult) {
                        is IsPlayerInClaimMenuResult.Success ->  {
                            if (claimMenuResult.isInClaimMenu) {
                                val previousOwner = Bukkit.getPlayer(previousOwnerId)
                                previousOwner?.closeInventory()
                                previousOwner?.sendActionBar(
                                    Component.text(localizationProvider.get(
                                        playerId, LocalizationKeys.FEEDBACK_TRANSFER_SUCCESS))
                                        .color(TextColor.color(255, 85, 85)))
                            }
                        }
                        is IsPlayerInClaimMenuResult.StorageError -> {}
                    }

                    // Navigate to next menu
                    ClaimManagementMenu(menuNavigator, player, claim, messageService).open()
                }
                else -> {
                    open()
                }
            }
        }
        thirdPane.addItem(confirmGuiItem, 0, 0)
        gui.resultComponent.addPane(thirdPane)
        gui.show(player)
    }
}
