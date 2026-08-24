package net.lumalyte.lg.interaction.menus.management

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.AnvilGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.actions.claim.metadata.UpdateClaimName
import net.lumalyte.lg.application.results.claim.metadata.UpdateClaimNameResult
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

class ClaimRenamingMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                        private val claim: Claim?): Menu, KoinComponent {
    private val lang: LangService by inject()
    private val updateClaimName: UpdateClaimName by inject()

    private var name = ""
    private var isConfirming = false

    override fun open() {
        if (claim == null) {
            player.sendMessage(lang.msg("menu.common.feedback.no_claim"))
            return
        }

        // Create homes menu
        val playerId = player.uniqueId
        val gui = AnvilGui(lang.guiTitle("menu.renaming.title"))
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
        val lodestoneItem = ItemStack.of(Material.BELL)
            .name(claim.name)
            .lore("${claim.position.x}, ${claim.position.y}, ${claim.position.z}")
        val guiItem = GuiItem(lodestoneItem) { guiEvent -> guiEvent.isCancelled = true }
        firstPane.addItem(guiItem, 0, 0)
        gui.firstItemComponent.addPane(firstPane)

        // Add message menu item if name is already taken
        val secondPane = StaticPane(0, 0, 1, 1)
        gui.secondItemComponent.addPane(secondPane)

        // Add confirm menu item.
        val thirdPane = StaticPane(0, 0, 1, 1)
        val confirmItem = ItemStack.of(Material.NETHER_STAR)
            .name(lang.gui("menu.common.item.confirm.name"))
        val confirmGuiItem = GuiItem(confirmItem) { guiEvent ->
            // Go back to edit menu if the name hasn't changed
            if (name == claim.name || name.isBlank()) {
                menuNavigator.goBack()
                return@GuiItem
            }

            // Attempt renaming
            val result = updateClaimName.execute(claim.id, name)
            when (result) {
                is UpdateClaimNameResult.Success -> menuNavigator.goBackWithData(result.claim)
                UpdateClaimNameResult.ClaimNotFound -> {
                    val paperItem = ItemStack.of(Material.PAPER)
                        .name(lang.gui("menu.renaming.item.unknown.name"))
                    val guiPaperItem = GuiItem(paperItem)
                    secondPane.addItem(guiPaperItem, 0, 0)
                    lodestoneItem.name(name)
                    isConfirming = true
                    gui.update()
                }
                UpdateClaimNameResult.NameAlreadyExists -> {
                    val paperItem = ItemStack.of(Material.PAPER)
                        .name(lang.gui("menu.renaming.item.existing.name"))
                    val guiPaperItem = GuiItem(paperItem) {guiEvent ->
                        secondPane.removeItem(0, 0)
                        lodestoneItem.name(name)
                        isConfirming = true
                        gui.update()
                    }
                    secondPane.addItem(guiPaperItem, 0, 0)
                    lodestoneItem.name(name)
                    isConfirming = true
                    gui.update()
                }
                else -> menuNavigator.goBack()
            }
        }

        thirdPane.addItem(confirmGuiItem, 0, 0)
        gui.resultComponent.addPane(thirdPane)
        gui.show(player)
    }
}

