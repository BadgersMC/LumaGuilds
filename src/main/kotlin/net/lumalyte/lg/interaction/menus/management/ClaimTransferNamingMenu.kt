package net.lumalyte.lg.interaction.menus.management

import net.badgersmc.nexus.i18n.LangService

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.AnvilGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.actions.claim.transfer.AcceptTransferRequest
import net.lumalyte.lg.application.actions.player.IsPlayerInClaimMenu
import net.lumalyte.lg.application.results.claim.transfer.AcceptTransferRequestResult
import net.lumalyte.lg.application.results.player.IsPlayerInClaimMenuResult
import net.lumalyte.lg.domain.entities.Claim
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

class ClaimTransferNamingMenu(private val menuNavigator: MenuNavigator, private val claim: Claim?,
                              private val player: Player): Menu, KoinComponent {
    private val lang: LangService by inject()
    private val acceptTransferRequest: AcceptTransferRequest by inject()
    private val isPlayerInClaimMenu: IsPlayerInClaimMenu by inject()

    var name = ""
    var previousResult: AcceptTransferRequestResult? = null

    override fun open() {
        if (claim == null) {
            player.sendMessage(lang.msg("menu.common.feedback.no_claim"))
            return
        }

        // Create transfer naming menu
        val playerId = player.uniqueId
        val gui = AnvilGui(lang.legacy("menu.naming.title"))
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent -> if (guiEvent.click == ClickType.SHIFT_LEFT ||
            guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true }

        // Add lodestone menu item
        val firstPane = StaticPane(0, 0, 1, 1)
        val lodestoneItem = ItemStack.of(Material.BELL)
            .name(name)
            .lore("${claim.position.x}, ${claim.position.y}, ${claim.position.z}")
        val guiItem = GuiItem(lodestoneItem) { guiEvent -> guiEvent.isCancelled = true }
        firstPane.addItem(guiItem, 0, 0)
        gui.firstItemComponent.addPane(firstPane)

        // Add message menu item if name is already taken
        val secondPane = StaticPane(0, 0, 1, 1)
        gui.secondItemComponent.addPane(secondPane)
        when (previousResult) {
            AcceptTransferRequestResult.NoActiveTransferRequest -> {
                val paperItem = ItemStack.of(Material.MAGMA_CREAM)
                    .name(lang.legacy("accept_transfer_condition.invalid_request"))
                val guiPaperItem = GuiItem(paperItem) { guiEvent -> guiEvent.isCancelled = true }
                secondPane.addItem(guiPaperItem, 0, 0)
            }
            AcceptTransferRequestResult.ClaimNotFound -> {
                val paperItem = ItemStack.of(Material.MAGMA_CREAM)
                    .name(lang.legacy("accept_transfer_condition.invalid_claim"))
                val guiPaperItem = GuiItem(paperItem) { guiEvent -> guiEvent.isCancelled = true }
                secondPane.addItem(guiPaperItem, 0, 0)
            }
            AcceptTransferRequestResult.BlockLimitExceeded -> {
                val paperItem = ItemStack.of(Material.MAGMA_CREAM)
                    .name(lang.legacy("creation_condition.blocks"))
                val guiPaperItem = GuiItem(paperItem) { guiEvent -> guiEvent.isCancelled = true }
                secondPane.addItem(guiPaperItem, 0, 0)
            }
            AcceptTransferRequestResult.ClaimLimitExceeded -> {
                val paperItem = ItemStack.of(Material.MAGMA_CREAM)
                    .name(lang.legacy("creation_condition.claims"))
                val guiPaperItem = GuiItem(paperItem) { guiEvent -> guiEvent.isCancelled = true }
                secondPane.addItem(guiPaperItem, 0, 0)
            }
            AcceptTransferRequestResult.NameAlreadyExists -> {
                val paperItem = ItemStack.of(Material.PAPER)
                    .name(lang.legacy("creation_condition.existing"))
                val guiPaperItem = GuiItem(paperItem) { guiEvent -> guiEvent.isCancelled = true }
                secondPane.addItem(guiPaperItem, 0, 0)
            }
            AcceptTransferRequestResult.PlayerOwnsClaim -> {
                val paperItem = ItemStack.of(Material.MAGMA_CREAM)
                    .name(lang.legacy("accept_transfer_condition.owner"))
                val guiPaperItem = GuiItem(paperItem) { guiEvent -> guiEvent.isCancelled = true }
                secondPane.addItem(guiPaperItem, 0, 0)
            }
            AcceptTransferRequestResult.StorageError -> {
                val paperItem = ItemStack.of(Material.MAGMA_CREAM)
                    .name(lang.legacy("general.error"))
                val guiPaperItem = GuiItem(paperItem) { guiEvent -> guiEvent.isCancelled = true }
                secondPane.addItem(guiPaperItem, 0, 0)
            }
            else -> {}
        }

        // Add confirm menu item.
        val thirdPane = StaticPane(0, 0, 1, 1)
        val confirmItem = ItemStack.of(Material.NETHER_STAR)
            .name(lang.legacy("menu.common.item.confirm.name"))
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
                                    Component.text(lang.legacy("feedback.transfer.success"))
                                        .color(TextColor.color(255, 85, 85)))
                            }
                        }
                        is IsPlayerInClaimMenuResult.StorageError -> {}
                    }

                    // Navigate to next menu
                    ClaimManagementMenu(menuNavigator, player, claim).open()
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
