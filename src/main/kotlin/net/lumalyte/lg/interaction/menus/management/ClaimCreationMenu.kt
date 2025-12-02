package net.lumalyte.lg.interaction.menus.management

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.actions.claim.IsNewClaimLocationValid
import net.lumalyte.lg.application.actions.claim.ListPlayerClaims
import net.lumalyte.lg.application.results.claim.IsNewClaimLocationValidResult
import net.lumalyte.lg.application.services.PlayerMetadataService
import net.lumalyte.lg.domain.values.LocalizationKeys
import net.lumalyte.lg.infrastructure.adapters.bukkit.toPosition2D
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ClaimCreationMenu(private val player: Player, private val menuNavigator: MenuNavigator,
                        private val location: Location): Menu, KoinComponent {
    private val localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider by inject()
    private val playerMetadataService: PlayerMetadataService by inject()
    private val listPlayerClaims: ListPlayerClaims by inject()
    private val isNewClaimLocationValid: IsNewClaimLocationValid by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    override fun open() {
        val playerId = player.uniqueId
        val gui = ChestGui(1, localizationProvider.get(playerId, LocalizationKeys.MENU_CREATION_TITLE))
        val pane = StaticPane(0, 0, 9, 1)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent -> if (guiEvent.click == ClickType.SHIFT_LEFT ||
            guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true }
        gui.addPane(pane)

        // Notify if player doesn't have enough claims
        val playerClaimCount = listPlayerClaims.execute(playerId).count()
        if (playerClaimCount >=
                playerMetadataService.getPlayerClaimLimit(playerId)) {
            val iconEditorItem = ItemStack(Material.MAGMA_CREAM)
                .name(localizationProvider.get(playerId, LocalizationKeys.MENU_CREATION_ITEM_CANNOT_CREATE_NAME))
                .lore(localizationProvider.get(playerId, LocalizationKeys.CREATION_CONDITION_CLAIMS))
            val guiIconEditorItem = GuiItem(iconEditorItem) { guiEvent -> guiEvent.isCancelled = true }
            pane.addItem(guiIconEditorItem, 4, 0)
            gui.show(player)
            return
        }

        // Change the button depending on whether the player is able to create the claim or not
        when (isNewClaimLocationValid.execute(location.toPosition2D(), location.world.uid)) {
            IsNewClaimLocationValidResult.Valid -> {
                val iconEditorItem = ItemStack(Material.BELL)
                    .name(localizationProvider.get(playerId, LocalizationKeys.MENU_CREATION_ITEM_CREATE_NAME))
                    .lore(localizationProvider.get(playerId, LocalizationKeys.MENU_CREATION_ITEM_CREATE_LORE_PROTECTED))
                    .lore(localizationProvider.get(playerId, LocalizationKeys.MENU_CREATION_ITEM_CREATE_LORE_REMAINING,
                        playerMetadataService.getPlayerClaimLimit(playerId) - playerClaimCount))
                val guiIconEditorItem = GuiItem(iconEditorItem) {
                    menuNavigator.openMenu(ClaimNamingMenu(player, menuNavigator, location))
                }
                pane.addItem(guiIconEditorItem, 4, 0)
                gui.show(player)
            }
            IsNewClaimLocationValidResult.Overlap -> {
                val iconEditorItem = ItemStack(Material.MAGMA_CREAM)
                    .name(localizationProvider.get(playerId, LocalizationKeys.MENU_CREATION_ITEM_CANNOT_CREATE_NAME))
                    .lore(localizationProvider.get(playerId, LocalizationKeys.CREATION_CONDITION_OVERLAP))
                val guiIconEditorItem = GuiItem(iconEditorItem) { guiEvent -> guiEvent.isCancelled = true }
                pane.addItem(guiIconEditorItem, 4, 0)
                gui.show(player)
                return
            }
            IsNewClaimLocationValidResult.TooCloseToWorldBorder -> {
                val iconEditorItem = ItemStack(Material.MAGMA_CREAM)
                    .name(localizationProvider.get(playerId, LocalizationKeys.MENU_CREATION_ITEM_CANNOT_CREATE_NAME))
                    .lore(localizationProvider.get(playerId, LocalizationKeys.CREATION_CONDITION_WORLD_BORDER))
                val guiIconEditorItem = GuiItem(iconEditorItem) { guiEvent -> guiEvent.isCancelled = true }
                pane.addItem(guiIconEditorItem, 4, 0)
                gui.show(player)
                return
            }
            IsNewClaimLocationValidResult.StorageError ->
                player.sendMessage(localizationProvider.get(playerId, LocalizationKeys.GENERAL_ERROR))
            else ->
                player.sendMessage(localizationProvider.get(playerId, LocalizationKeys.GENERAL_ERROR))

        }
    }
}

