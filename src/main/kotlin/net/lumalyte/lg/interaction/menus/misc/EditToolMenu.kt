package net.lumalyte.lg.interaction.menus.misc

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimBlockCount
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.partition.CanRemovePartition
import net.lumalyte.lg.application.actions.claim.partition.GetClaimPartitions
import net.lumalyte.lg.application.actions.claim.partition.RemovePartition
import net.lumalyte.lg.application.actions.player.DoesPlayerHaveClaimOverride
import net.lumalyte.lg.application.actions.player.RegisterClaimMenuOpening
import net.lumalyte.lg.application.actions.player.visualisation.ClearVisualisation
import net.lumalyte.lg.application.actions.player.visualisation.DisplayVisualisation
import net.lumalyte.lg.application.actions.player.visualisation.GetVisualiserMode
import net.lumalyte.lg.application.actions.player.visualisation.ToggleVisualiserMode
import net.lumalyte.lg.application.events.PartitionModificationEvent
import net.lumalyte.lg.application.results.claim.partition.CanRemovePartitionResult
import net.lumalyte.lg.application.results.player.DoesPlayerHaveClaimOverrideResult
import net.lumalyte.lg.application.results.player.visualisation.GetVisualiserModeResult
import net.lumalyte.lg.domain.entities.Partition
import net.lumalyte.lg.infrastructure.adapters.bukkit.toPosition3D
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.common.ConfirmationMenu
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EditToolMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                   private val partition: Partition? = null): Menu, KoinComponent {
    private val lang: LangService by inject()
    private val getVisualiserMode: GetVisualiserMode by inject()
    private val toggleVisualiserMode: ToggleVisualiserMode by inject()
    private val getClaimDetails: GetClaimDetails by inject()
    private val getClaimBlockCount: GetClaimBlockCount by inject()
    private val getClaimPartitions: GetClaimPartitions by inject()
    private val displayVisualisation: DisplayVisualisation by inject()
    private val clearVisualisation: ClearVisualisation by inject()
    private val removePartition: RemovePartition by inject()
    private val registerClaimMenuOpening: RegisterClaimMenuOpening by inject()
    private val canRemovePartition: CanRemovePartition by inject()
    private val doesPlayerHaveClaimOverride: DoesPlayerHaveClaimOverride by inject()
    private val menuFactory: MenuFactory by inject()

    override fun open() {
        val title = lang.guiTitle("menu.edit_tool.title")
        val gui = ChestGui(1, title)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent -> if (guiEvent.click == ClickType.SHIFT_LEFT ||
            guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true }

        val pane = StaticPane(0, 0, 9, 1)
        gui.addPane(pane)

        // Get visualiser mode
        val visualiserMode = when (val result = getVisualiserMode.execute(player.uniqueId)) {
            GetVisualiserModeResult.StorageError -> 0
            is GetVisualiserModeResult.Success -> result.visualiserMode
        }

        // Add mode switch icon
        val modeSwitchItem = ItemStack.of(Material.SPYGLASS)
            .name(lang.gui( "menu.edit_tool.item.change_mode.name"))
        val guiModeSwitchItem: GuiItem
        if (visualiserMode == 0) {
            modeSwitchItem.lore(lang.gui("menu.edit_tool.item.change_mode.lore.view_active"))
            modeSwitchItem.lore(lang.gui("menu.edit_tool.item.change_mode.lore.edit"))

            guiModeSwitchItem = GuiItem(modeSwitchItem) {
                toggleVisualiserMode.execute(player.uniqueId)
                clearVisualisation.execute(player.uniqueId)
                displayVisualisation.execute(player.uniqueId, player.location.toPosition3D())
                open()
            }
        }
        else {
            modeSwitchItem.lore(lang.gui("menu.edit_tool.item.change_mode.lore.view"))
            modeSwitchItem.lore(lang.gui("menu.edit_tool.item.change_mode.lore.edit_active"))
            guiModeSwitchItem = GuiItem(modeSwitchItem) {
                toggleVisualiserMode.execute(player.uniqueId)
                clearVisualisation.execute(player.uniqueId)
                displayVisualisation.execute(player.uniqueId, player.location.toPosition3D())
                open()
            }
        }

        pane.addItem(guiModeSwitchItem, 0, 0)

        // Add divider
        val dividerItem = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE).name(" ")
        val guiDividerItem = GuiItem(dividerItem) { guiEvent -> guiEvent.isCancelled = true }
        pane.addItem(guiDividerItem, 1, 0)

        // Add a message item if selection is out of any claim
        if (partition == null) {
            val messageItem = ItemStack.of(Material.COAL)
                .name(lang.gui("menu.edit_tool.item.no_claim.name"))
                .lore(lang.gui("menu.edit_tool.item.no_claim.lore"))

            val guiMessageItem = GuiItem(messageItem) { guiEvent -> guiEvent.isCancelled = true }
            pane.addItem(guiMessageItem, 5, 0)
            gui.show(player)
            return
        }

        // Get claim override value
        val result = doesPlayerHaveClaimOverride.execute(player.uniqueId)
        val hasOverride = when (result) {
            DoesPlayerHaveClaimOverrideResult.StorageError -> false
            is DoesPlayerHaveClaimOverrideResult.Success -> result.hasOverride
        }

        // Add a message if the player doesn't own the claim
        val claim = getClaimDetails.execute(partition.claimId) ?: return
        if (claim.playerId != player.uniqueId && !hasOverride) {
            val messageItem = ItemStack.of(Material.COAL)
                .name(lang.gui("menu.edit_tool.item.no_permission.name"))
                .lore(lang.gui("menu.edit_tool.item.no_permission.lore"))

            val guiMessageItem = GuiItem(messageItem) { guiEvent -> guiEvent.isCancelled = true }
            pane.addItem(guiMessageItem, 5, 0)
            gui.show(player)
            return
        }

        // Add a claim information item
        val partitions = getClaimPartitions.execute(claim.id)
        val blockCount = getClaimBlockCount.execute(claim.id)
        val claimItem = ItemStack.of(Material.BELL)
            .name(lang.gui("menu.edit_tool.item.claim.name"))
            .lore(lang.gui("menu.edit_tool.item.claim.lore.claim_name", "claim" to claim.name))
            .lore(lang.gui(
                "menu.edit_tool.item.claim.lore.location",
                "x" to claim.position.x,
                "y" to claim.position.y,
                "z" to claim.position.z,
            ))
            .lore(lang.gui("menu.edit_tool.item.claim.lore.partitions", "partition_count" to partitions.count()))
            .lore(lang.gui("menu.edit_tool.item.claim.lore.blocks", "blocks" to blockCount))
        val guiClaimItem = GuiItem(claimItem) { guiEvent -> guiEvent.isCancelled = true }
        pane.addItem(guiClaimItem, 3, 0)

        // Add partition information item
        val partitionItem = ItemStack.of(Material.PAPER)
            .name(lang.gui( "menu.edit_tool.item.partition.name"))
            .lore(lang.gui(
                "menu.edit_tool.item.partition.lore.location",
                "lower_x" to partition.area.lowerPosition2D.x,
                "lower_z" to partition.area.lowerPosition2D.z,
                "upper_x" to partition.area.upperPosition2D.x,
                "upper_z" to partition.area.upperPosition2D.z,
            ))
            .lore(lang.gui("menu.edit_tool.item.partition.lore.blocks", "blocks" to partition.area.getBlockCount()))
        val guiPartitionItem = GuiItem(partitionItem) { guiEvent -> guiEvent.isCancelled = true }
        pane.addItem(guiPartitionItem, 5, 0)

        // Change button depending on if partition can be removed
        when (canRemovePartition.execute(partition.id)) {
            CanRemovePartitionResult.Success -> {
                val deleteItem = ItemStack.of(Material.REDSTONE)
                    .name(lang.gui("menu.edit_tool.item.delete.name"))
                val deleteTitle = lang.guiTitle("menu.confirm_partition_delete.title")
                val confirmAction: () -> Unit = {
                    removePartition.execute(partition.id)
                    val event = PartitionModificationEvent(partition)
                    event.callEvent()
                    player.closeInventory()
                }
                val guiDeleteItem = GuiItem(deleteItem) {
                    menuNavigator.openMenu(menuFactory.createConfirmationMenu(menuNavigator, player, deleteTitle, confirmAction)) }
                pane.addItem(guiDeleteItem, 7, 0)
            }
            else -> {
                val deleteItem = ItemStack.of(Material.GUNPOWDER)
                    .name(lang.gui("menu.edit_tool.item.cannot_delete.name"))
                    .lore(lang.gui("menu.edit_tool.item.cannot_delete.lore"))
                val guiDeleteItem = GuiItem(deleteItem) { guiEvent -> guiEvent.isCancelled = true }
                pane.addItem(guiDeleteItem, 7, 0)
            }

        }

        registerClaimMenuOpening.execute(player.uniqueId, claim.id)
        gui.show(player)
    }
}

