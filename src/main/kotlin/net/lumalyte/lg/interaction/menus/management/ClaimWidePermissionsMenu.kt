package net.lumalyte.lg.interaction.menus.management

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.actions.claim.permission.GetClaimPermissions
import net.lumalyte.lg.application.actions.claim.permission.GrantAllClaimWidePermissions
import net.lumalyte.lg.application.actions.claim.permission.GrantClaimWidePermission
import net.lumalyte.lg.application.actions.claim.permission.RevokeAllClaimWidePermissions
import net.lumalyte.lg.application.actions.claim.permission.RevokeClaimWidePermission
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.values.ClaimPermission
import net.lumalyte.lg.domain.values.LocalizationKeys
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.getIcon
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

class ClaimWidePermissionsMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                               private val claim: Claim): Menu, KoinComponent {
    private val localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider by inject()
    private val grantAllClaimWidePermissions : GrantAllClaimWidePermissions by inject()
    private val revokeAllClaimWidePermissions: RevokeAllClaimWidePermissions by inject()
    private val getClaimPermissions: GetClaimPermissions by inject()
    private val grantClaimWidePermission: GrantClaimWidePermission by inject()
    private val revokeClaimWidePermission: RevokeClaimWidePermission by inject()

    override fun open() {
        // Create player permissions menu
        val playerId = player.uniqueId
        val gui = ChestGui(6, localizationProvider.get(playerId, LocalizationKeys.MENU_CLAIM_WIDE_PERMISSIONS_TITLE))
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent -> if (guiEvent.click == ClickType.SHIFT_LEFT ||
            guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true }

        // Add controls
        val controlsPane = addControlsSection(playerId, gui) { menuNavigator.goBack() }

        val deselectAction: () -> Unit = {
            revokeAllClaimWidePermissions.execute(claim.id)
            open()
        }

        val selectAction: () -> Unit = {
            grantAllClaimWidePermissions.execute(claim.id)
            open()
        }

        addSelector(playerId, controlsPane, ItemStack(Material.BELL)
            .name(localizationProvider.get(playerId, LocalizationKeys.MENU_CLAIM_WIDE_PERMISSIONS_ITEM_INFO_NAME)),
            deselectAction, selectAction)

        // Add horizontal divider
        val dividerItem = ItemStack(Material.BLACK_STAINED_GLASS_PANE).name(" ")
        val guiDividerItem = GuiItem(dividerItem) { guiEvent -> guiEvent.isCancelled = true }

        // Add vertical divider
        val verticalDividerPane = StaticPane(4, 2, 1, 6)
        gui.addPane(verticalDividerPane)
        for (slot in 0..3) {
            verticalDividerPane.addItem(guiDividerItem, 0, slot)
        }

        val enabledPermissions = getClaimPermissions.execute(claim.id)
        val disabledPermissions = ClaimPermission.entries.toTypedArray().subtract(enabledPermissions)

        // Add list of disabled permissions
        val disabledPermissionsPane = StaticPane(0, 2, 4, 4)
        gui.addPane(disabledPermissionsPane)
        var xSlot = 0
        var ySlot = 0
        for (permission in disabledPermissions) {
            val permissionItem = permission.getIcon(localizationProvider, playerId)

            val guiPermissionItem = GuiItem(permissionItem) {
                grantClaimWidePermission.execute(claim.id, permission)
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
            val permissionItem = permission.getIcon(localizationProvider, playerId)

            val guiPermissionItem = GuiItem(permissionItem) {
                revokeClaimWidePermission.execute(claim.id, permission)
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
        val dividerItem = ItemStack(Material.BLACK_STAINED_GLASS_PANE).name(" ")
        for (slot in 0..8) {
            val guiDividerItem = GuiItem(dividerItem) { guiEvent -> guiEvent.isCancelled = true }
            dividerPane.addItem(guiDividerItem, slot, 0)
        }

        // Add controls pane
        val controlsPane = StaticPane(0, 0, 9, 1)
        gui.addPane(controlsPane)

        // Add go back item
        val exitItem = ItemStack(Material.NETHER_STAR)
            .name(localizationProvider.get(playerId, LocalizationKeys.MENU_COMMON_ITEM_BACK_NAME))

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
        val deselectItem = ItemStack(Material.HONEY_BLOCK)
            .name(localizationProvider.get(playerId, LocalizationKeys.MENU_COMMON_ITEM_DESELECT_ALL_NAME))
        val guiDeselectItem = GuiItem(deselectItem) { deselectAction() }
        controlsPane.addItem(guiDeselectItem, 2, 0)

        // Add select all button
        val selectItem = ItemStack(Material.SLIME_BLOCK)
            .name(localizationProvider.get(playerId, LocalizationKeys.MENU_COMMON_ITEM_SELECT_ALL_NAME))
        val guiSelectItem = GuiItem(selectItem) { selectAction() }
        controlsPane.addItem(guiSelectItem, 6, 0)
    }
}
