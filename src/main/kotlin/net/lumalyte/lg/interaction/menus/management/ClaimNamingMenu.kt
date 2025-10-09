package net.lumalyte.lg.interaction.menus.management

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.AnvilGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.actions.claim.CreateClaim
import net.lumalyte.lg.domain.values.LocalizationKeys
import net.lumalyte.lg.infrastructure.adapters.bukkit.toPosition3D
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class ClaimNamingMenu(private val player: Player, private val menuNavigator: MenuNavigator,
                        private val location: Location, private val messageService: MessageService): Menu, KoinComponent {
    private val localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider by inject()
    private val createClaim: CreateClaim by inject()
    private var name = ""
    private var isConfirming = false

    override fun open() {
        // Create homes menu
        val playerId = player.uniqueId
        val gui = AnvilGui(localizationProvider.get(playerId, LocalizationKeys.MENU_NAMING_TITLE))
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent -> if (guiEvent.click == ClickType.SHIFT_LEFT ||
            guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true }
        gui.setOnNameInputChanged { newName ->
            if (!isConfirming) {
                name = newName
            } else {
                isConfirming = false
            }
        }

        // Add bell menu item
        val firstPane = StaticPane(0, 0, 1, 1)
        val bellItem = ItemStack(Material.BELL)
            .name("")
            .addAdventureLore(player, messageService, "${location.blockX}, ${location.blockY}, ${location.blockZ}")
        val guiItem = GuiItem(bellItem) { guiEvent -> guiEvent.isCancelled = true }
        firstPane.addItem(guiItem, 0, 0)
        gui.firstItemComponent.addPane(firstPane)

        // Add message menu item if name is already taken
        val secondPane = StaticPane(0, 0, 1, 1)
        gui.secondItemComponent.addPane(secondPane)

        // Add confirm menu item.
        val thirdPane = StaticPane(0, 0, 1, 1)
        val confirmItem = ItemStack(Material.NETHER_STAR)
            .name(localizationProvider.get(playerId, LocalizationKeys.MENU_COMMON_ITEM_CONFIRM_NAME))
        val confirmGuiItem = GuiItem(confirmItem) { guiEvent ->
            val result = createClaim.execute(player.uniqueId, name, location.toPosition3D(), location.world.uid)
            when (result) {
                is net.lumalyte.lg.application.results.claim.CreateClaimResult.Success -> {
                    location.world.playSound(
                        player.location,
                        Sound.BLOCK_VAULT_OPEN_SHUTTER,
                        SoundCategory.BLOCKS,
                        1.0f,
                        1.0f
                    )
                    menuNavigator.openMenu(ClaimManagementMenu(menuNavigator, player, result.claim, messageService))
                }

                is net.lumalyte.lg.application.results.claim.CreateClaimResult.LimitExceeded -> {
                    val paperItem = ItemStack(Material.PAPER)
                        .name(localizationProvider.get(playerId, LocalizationKeys.CREATION_CONDITION_CLAIMS))
                    val guiPaperItem = GuiItem(paperItem) { guiEvent ->
                        secondPane.removeItem(0, 0)
                        bellItem.name(name)
                        isConfirming = true
                        gui.update()
                    }
                    secondPane.addItem(guiPaperItem, 0, 0)
                    bellItem.name(name)
                    isConfirming = true
                    gui.update()
                }

                is net.lumalyte.lg.application.results.claim.CreateClaimResult.NameAlreadyExists -> {
                    val paperItem = ItemStack(Material.PAPER)
                        .name(localizationProvider.get(playerId, LocalizationKeys.CREATION_CONDITION_EXISTING))
                    val guiPaperItem = GuiItem(paperItem) { guiEvent ->
                        secondPane.removeItem(0, 0)
                        bellItem.name(name)
                        isConfirming = true
                        gui.update()
                    }
                    secondPane.addItem(guiPaperItem, 0, 0)
                    bellItem.name(name)
                    isConfirming = true
                    gui.update()
                }

                is net.lumalyte.lg.application.results.claim.CreateClaimResult.NameCannotBeBlank -> {
                    val paperItem = ItemStack(Material.PAPER)
                        .name(localizationProvider.get(playerId, LocalizationKeys.CREATION_CONDITION_UNNAMED))
                    val guiPaperItem = GuiItem(paperItem) { guiEvent ->
                        secondPane.removeItem(0, 0)
                        bellItem.name(name)
                        isConfirming = true
                        gui.update()
                    }
                    secondPane.addItem(guiPaperItem, 0, 0)
                    bellItem.name("")
                    gui.update()
                }
                is net.lumalyte.lg.application.results.claim.CreateClaimResult.TooCloseToWorldBorder -> {
                    val paperItem = ItemStack(Material.PAPER)
                        .name(localizationProvider.get(playerId, LocalizationKeys.CREATION_CONDITION_WORLD_BORDER))
                    val guiPaperItem = GuiItem(paperItem) { guiEvent ->
                        secondPane.removeItem(0, 0)
                        bellItem.name(name)
                        isConfirming = true
                        gui.update()
                    }
                    secondPane.addItem(guiPaperItem, 0, 0)
                    bellItem.name(name)
                    gui.update()
                }
            }
        }

        // GUI display
        thirdPane.addItem(confirmGuiItem, 0, 0)
        gui.resultComponent.addPane(thirdPane)
        gui.show(player)
    }
}
