package net.lumalyte.lg.interaction.menus.management

import net.badgersmc.nexus.i18n.LangService

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.actions.claim.permission.GetClaimPlayerPermissions
import net.lumalyte.lg.application.actions.claim.permission.GrantAllPlayerClaimPermissions
import net.lumalyte.lg.application.actions.claim.permission.GrantPlayerClaimPermission
import net.lumalyte.lg.application.actions.claim.permission.RevokeAllPlayerClaimPermissions
import net.lumalyte.lg.application.actions.claim.permission.RevokePlayerClaimPermission
import net.lumalyte.lg.application.actions.claim.transfer.CanPlayerReceiveTransferRequest
import net.lumalyte.lg.application.actions.claim.transfer.DoesPlayerHaveTransferRequest
import net.lumalyte.lg.application.actions.claim.transfer.OfferPlayerTransferRequest
import net.lumalyte.lg.application.actions.claim.transfer.WithdrawPlayerTransferRequest
import net.lumalyte.lg.application.results.claim.transfer.CanPlayerReceiveTransferRequestResult
import net.lumalyte.lg.application.results.claim.transfer.DoesPlayerHaveTransferRequestResult
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.values.ClaimPermission
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.common.ConfirmationMenu
import net.lumalyte.lg.utils.createHead
import net.lumalyte.lg.utils.getIcon
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

class ClaimPlayerPermissionsMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                                 private val claim: Claim?, private val targetPlayer: OfflinePlayer?
): Menu, KoinComponent {
    private val lang: LangService by inject()
    private val getPlayerClaimPermissions: GetClaimPlayerPermissions by inject()
    private val grantAllPlayerClaimPermissions: GrantAllPlayerClaimPermissions by inject()
    private val grantPlayerClaimPermission: GrantPlayerClaimPermission by inject()
    private val revokePlayerClaimPermission: RevokePlayerClaimPermission by inject()
    private val revokeAllPlayerClaimPermissions: RevokeAllPlayerClaimPermissions by inject()
    private val canPlayerReceiveTransferRequest: CanPlayerReceiveTransferRequest by inject()
    private val doesPlayerHaveTransferRequest: DoesPlayerHaveTransferRequest by inject()
    private val offerPlayerTransferRequest: OfferPlayerTransferRequest by inject()
    private val withdrawPlayerTransferRequest: WithdrawPlayerTransferRequest by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    override fun open() {
        // Validate that claim and targetPlayer are provided
        val validClaim = claim ?: run {
            player.sendMessage("§cError: No claim available")
            return
        }
        val validTarget = targetPlayer ?: run {
            player.sendMessage("§cError: No target player available")
            return
        }

        // Create player permissions menu
        val playerId = player.uniqueId
        val gui = ChestGui(6, lang.legacy("menu.player_permissions.title", "player" to validTarget.name))
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent -> if (guiEvent.click == ClickType.SHIFT_LEFT ||
            guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true }

        // Add controls pane
        val controlsPane = addControlsSection(playerId, gui) { menuNavigator.goBack() }

        val deselectAction: () -> Unit = {
            revokeAllPlayerClaimPermissions.execute(validClaim.id, validTarget.uniqueId)
            open()
        }

        val selectAction: () -> Unit = {
            grantAllPlayerClaimPermissions.execute(validClaim.id, validTarget.uniqueId)
            open()
        }

        addSelector(playerId, controlsPane, createHead(validTarget).name(validTarget.name ?:
            lang.legacy("general.name_error")), deselectAction, selectAction)

        val transferRequestResult = doesPlayerHaveTransferRequest.execute(validClaim.id, validTarget.uniqueId)

        val guiTransferRequestItem: GuiItem
        when (transferRequestResult) {
            is DoesPlayerHaveTransferRequestResult.ClaimNotFound -> {
                val transferRequestItem = ItemStack.of(Material.MAGMA_CREAM)
                    .name("menu.player_permissions.item.cannot_transfer.name")
                    .lore("send_transfer_condition.exist")
                guiTransferRequestItem = GuiItem(transferRequestItem)
            }
            is DoesPlayerHaveTransferRequestResult.StorageError -> {
                val transferRequestItem = ItemStack.of(Material.MAGMA_CREAM)
                    .name(lang.legacy("menu.common.item.error.name"))
                    .lore(lang.legacy("menu.common.item.error.lore"))
                guiTransferRequestItem = GuiItem(transferRequestItem)
            }
            is DoesPlayerHaveTransferRequestResult.Success -> {
                guiTransferRequestItem = createTransferButton(playerId, validClaim, validTarget, transferRequestResult.hasRequest)
            }
        }
        controlsPane.addItem(guiTransferRequestItem, 8, 0)

        // Add vertical divider
        val dividerItem = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE).name(" ")
        val guiDividerItem = GuiItem(dividerItem) { guiEvent -> guiEvent.isCancelled = true }
        val verticalDividerPane = StaticPane(4, 2, 1, 6)
        gui.addPane(verticalDividerPane)
        for (slot in 0..3) {
            verticalDividerPane.addItem(guiDividerItem, 0, slot)
        }

        val enabledPermissions = getPlayerClaimPermissions.execute(validClaim.id, validTarget.uniqueId)
        val disabledPermissions = ClaimPermission.entries.toTypedArray().subtract(enabledPermissions)

        // Add list of disabled permissions
        val disabledPermissionsPane = StaticPane(0, 2, 4, 4)
        gui.addPane(disabledPermissionsPane)
        var xSlot = 0
        var ySlot = 0
        for (permission in disabledPermissions) {
            val permissionItem = permission.getIcon(lang, playerId)

            val guiPermissionItem = GuiItem(permissionItem) {
                grantPlayerClaimPermission.execute(validClaim.id, validTarget.uniqueId, permission)
                open()
            }

            disabledPermissionsPane.addItem(guiPermissionItem , xSlot, ySlot)

            // Increment slot
            xSlot += 1
            if (xSlot > 3) {
                xSlot = 0
                ySlot += 1
            }
        }

        val enabledPermissionsPane = StaticPane(5, 2, 4, 4)
        gui.addPane(enabledPermissionsPane)
        xSlot = 0
        ySlot = 0
        for (permission in enabledPermissions) {
            val permissionItem = permission.getIcon(lang, playerId)

            val guiPermissionItem = GuiItem(permissionItem) {
                revokePlayerClaimPermission.execute(validClaim.id, validTarget.uniqueId, permission)
                open()
            }

            enabledPermissionsPane.addItem(guiPermissionItem , xSlot, ySlot)

            // Increment slot
            xSlot += 1
            if (xSlot > 3) {
                xSlot = 0
                ySlot += 1
            }
        }

        gui.show(player)
    }

    private fun addControlsSection(playerId: UUID, gui: ChestGui, backButtonAction: () -> Unit): StaticPane {
        // Add divider
        val dividerPane = StaticPane(0, 1, 9, 1)
        gui.addPane(dividerPane)
        val dividerItem = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE).name(" ")
        for (slot in 0..8) {
            val guiDividerItem = GuiItem(dividerItem) { guiEvent -> guiEvent.isCancelled = true }
            dividerPane.addItem(guiDividerItem, slot, 0)
        }

        // Add controls pane
        val controlsPane = StaticPane(0, 0, 9, 1)
        gui.addPane(controlsPane)

        // Add go back item
        val exitItem = ItemStack.of(Material.NETHER_STAR)
            .name(lang.legacy("menu.common.item.back.name"))

        val guiExitItem = GuiItem(exitItem) { backButtonAction() }
        controlsPane.addItem(guiExitItem, 0, 0)
        return controlsPane
    }

    private fun addSelector(playerId: UUID, controlsPane: StaticPane, displayItem: ItemStack,
                            deselectAction: () -> Unit, selectAction: () -> Unit) {
        // Add display item
        val guiDisplayItem = GuiItem(displayItem) { guiEvent -> guiEvent.isCancelled = true }
        controlsPane.addItem(guiDisplayItem, 4, 0)

        // Add deselect all button
        val deselectItem = ItemStack.of(Material.HONEY_BLOCK)
            .name(lang.legacy("menu.common.item.deselect_all.name"))
        val guiDeselectItem = GuiItem(deselectItem) { deselectAction() }
        controlsPane.addItem(guiDeselectItem, 2, 0)

        // Add select all button
        val selectItem = ItemStack.of(Material.SLIME_BLOCK)
            .name(lang.legacy("menu.common.item.select_all.name"))
        val guiSelectItem = GuiItem(selectItem) { selectAction() }
        controlsPane.addItem(guiSelectItem, 6, 0)
    }

    private fun createTransferButton(playerId: UUID, claim: Claim, targetPlayer: OfflinePlayer, hasRequest: Boolean): GuiItem {
        val guiTransferRequestItem: GuiItem
        if (hasRequest) {
            // Cancel the transfer request if it is pending
            val transferClaimItem = ItemStack.of(Material.BARRIER)
                .name(lang.legacy("menu.player_permissions.item.cancel_transfer.name"))
                .lore(lang.legacy("menu.player_permissions.item.cancel_transfer.lore"))
            guiTransferRequestItem = GuiItem(transferClaimItem) {
                withdrawPlayerTransferRequest.execute(claim.id, targetPlayer.uniqueId)
                open()
            }
        } else {
            // Send the transfer request if there is none pending
            val transferClaimAction: () -> Unit = {
                val confirmAction: () -> Unit = {
                    offerPlayerTransferRequest.execute(claim.id, targetPlayer.uniqueId)
                    open()
                }

                menuNavigator.openMenu(menuFactory.createConfirmationMenu(menuNavigator, player, lang.legacy("menu.transfer_send.title"), confirmAction))
            }
            when (canPlayerReceiveTransferRequest.execute(claim.id, targetPlayer.uniqueId)) {
                CanPlayerReceiveTransferRequestResult.Success -> {
                    val transferClaimItem = ItemStack.of(Material.BELL)
                        .name(lang.legacy("menu.player_permissions.item.transfer.name"))
                        .lore(lang.legacy("menu.player_permissions.item.transfer.lore", "player" to targetPlayer.name))
                    guiTransferRequestItem = GuiItem(transferClaimItem) { transferClaimAction() }
                }
                CanPlayerReceiveTransferRequestResult.ClaimLimitExceeded -> {
                    val transferClaimItem = ItemStack.of(Material.MAGMA_CREAM)
                        .name(lang.legacy("menu.player_permissions.item.cannot_transfer.name"))
                        .lore("send_transfer_condition.claims")
                    guiTransferRequestItem = GuiItem(transferClaimItem)
                }
                CanPlayerReceiveTransferRequestResult.BlockLimitExceeded -> {
                    val transferClaimItem = ItemStack.of(Material.MAGMA_CREAM)
                        .name(lang.legacy("menu.player_permissions.item.cannot_transfer.name"))
                        .lore("send_transfer_condition.blocks")
                    guiTransferRequestItem = GuiItem(transferClaimItem)
                }
                CanPlayerReceiveTransferRequestResult.ClaimNotFound -> {
                    val transferClaimItem = ItemStack.of(Material.MAGMA_CREAM)
                        .name(lang.legacy("menu.player_permissions.item.cannot_transfer.name"))
                        .lore("send_transfer_condition.exist")
                    guiTransferRequestItem = GuiItem(transferClaimItem)
                }
                CanPlayerReceiveTransferRequestResult.PlayerOwnsClaim -> {
                    val transferClaimItem = ItemStack.of(Material.MAGMA_CREAM)
                        .name(lang.legacy("menu.player_permissions.item.cannot_transfer.name"))
                        .lore("send_transfer_condition.owner")
                    guiTransferRequestItem = GuiItem(transferClaimItem)
                }
                CanPlayerReceiveTransferRequestResult.StorageError -> {
                    val transferClaimItem = ItemStack.of(Material.MAGMA_CREAM)
                        .name(lang.legacy("menu.common.item.error.name"))
                        .lore(lang.legacy("menu.common.item.error.lore"))
                    guiTransferRequestItem = GuiItem(transferClaimItem)
                }
            }
        }
        return guiTransferRequestItem
    }
}

