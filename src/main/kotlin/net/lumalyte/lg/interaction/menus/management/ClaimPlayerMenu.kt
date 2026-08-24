package net.lumalyte.lg.interaction.menus.management

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.actions.claim.permission.GetPlayersWithPermissionInClaim
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.createHead
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID
import kotlin.math.ceil

class ClaimPlayerMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                      private val claim: Claim?): Menu, KoinComponent {
    private val lang: LangService by inject()
    private val getPlayersWithPermissionInClaim: GetPlayersWithPermissionInClaim by inject()

    private var page = 0

    override fun open() {
        if (claim == null) {
            player.sendMessage(lang.msg("menu.common.feedback.no_claim"))
            return
        }

        // Create trust menu
        val playerId = player.uniqueId
        val gui = ChestGui(6, lang.guiTitle("menu.all_players.title"))
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent -> if (guiEvent.click == ClickType.SHIFT_LEFT ||
            guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true }

        // Add controls
        val controlsPane = addControlsSection(playerId, gui) { menuNavigator.goBack() }
        val trustedPlayers = getPlayersWithPermissionInClaim.execute(claim.id)
        addPaginator(playerId, controlsPane, page, ceil(trustedPlayers.count() / 36.0).toInt())

        // Add player search item
        val playerSearchItem = ItemStack.of(Material.NAME_TAG)
            .name(lang.gui("menu.all_players.item.search.name"))
            .lore(lang.gui("menu.all_players.item.search.lore"))
        val guiPlayerSearchItem = GuiItem(playerSearchItem) {
            menuNavigator.openMenu(ClaimPlayerSearchMenu(menuNavigator, claim, player)) }
        controlsPane.addItem(guiPlayerSearchItem, 3, 0)

        // Add list of players
        val warpsPane = StaticPane(0, 2, 9, 4)
        gui.addPane(warpsPane)
        var xSlot = 0
        var ySlot = 0
        for (targetPlayer in Bukkit.getOnlinePlayers()) {
            if (targetPlayer.uniqueId == claim.playerId) {
                continue
            }

            val warpItem = createHead(Bukkit.getOfflinePlayer(targetPlayer.uniqueId))
                .name("${Bukkit.getOfflinePlayer(targetPlayer.uniqueId).name}")
            val guiWarpItem = GuiItem(warpItem) {
                menuNavigator.openMenu(ClaimPlayerPermissionsMenu(menuNavigator, player, claim, targetPlayer))
            }
            warpsPane.addItem(guiWarpItem, xSlot, ySlot)

            // Increment slot
            xSlot += 1
            if (xSlot > 8) {
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
            .name(lang.gui("menu.common.item.back.name"))

        val guiExitItem = GuiItem(exitItem) { backButtonAction() }
        controlsPane.addItem(guiExitItem, 0, 0)
        return controlsPane
    }

    private fun addPaginator(playerId: UUID, controlsPane: StaticPane, currentPage: Int, totalPages: Int) {
        // Add prev item
        val prevItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.common.item.prev.name"))
        val guiPrevItem = GuiItem(prevItem) { guiEvent -> guiEvent.isCancelled = true }
        controlsPane.addItem(guiPrevItem, 6, 0)

        // Add page item
        val pageItem = ItemStack.of(Material.PAPER)
            .name(lang.gui("menu.common.item.page.name", "current_page" to currentPage, "total_pages" to totalPages))
        val guiPageItem = GuiItem(pageItem) { guiEvent -> guiEvent.isCancelled = true }
        controlsPane.addItem(guiPageItem, 7, 0)

        // Add next item
        val nextItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.common.item.next.name"))
        val guiNextItem = GuiItem(nextItem) { guiEvent -> guiEvent.isCancelled = true }
        controlsPane.addItem(guiNextItem, 8, 0)
    }
}

