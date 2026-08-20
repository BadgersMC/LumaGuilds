package net.lumalyte.lg.interaction.menus.management

import net.badgersmc.nexus.i18n.LangService

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.actions.claim.permission.GetClaimPlayerPermissions
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

class ClaimTrustMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                     private val claim: Claim?): Menu, KoinComponent {
    private val lang: LangService by inject()
    private val getPlayersWithPermissionInClaim: GetPlayersWithPermissionInClaim by inject()
    private val getClaimPlayerPermissions: GetClaimPlayerPermissions by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    private var page = 1

    override fun open() {
        if (claim == null) {
            player.sendMessage("§cError: No claim available")
            return
        }

        // Create trust menu
        val playerId = player.uniqueId
        val gui = ChestGui(6, lang.legacy("menu.trusted_players.title"))
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent -> if (guiEvent.click == ClickType.SHIFT_LEFT ||
            guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true }

        // Add controls pane
        val controlsPane = addControlsSection(playerId, gui) { menuNavigator.goBack() }
        val trustedPlayers = getPlayersWithPermissionInClaim.execute(claim.id)
        addPaginator(playerId, controlsPane, page, ceil(trustedPlayers.count() / 36.0).toInt())

        // Add default permissions button
        val defaultPermsItem = ItemStack.of(Material.LECTERN)
            .name(lang.legacy("menu.trusted_players.item.default_permissions.name"))
            .lore(lang.legacy("menu.trusted_players.item.default_permissions.lore"))
        val guiDefaultPermsItem = GuiItem(defaultPermsItem) {
            menuNavigator.openMenu(menuFactory.createClaimWidePermissionsMenu(menuNavigator, player, claim)) }
        controlsPane.addItem(guiDefaultPermsItem, 2, 0)

        // Add all players menu
        val allPlayersItem = ItemStack.of(Material.PLAYER_HEAD)
            .name(lang.legacy("menu.trusted_players.item.all_players.name"))
            .lore(lang.legacy("menu.trusted_players.item.all_players.lore"))
        val guiAllPlayersItem = GuiItem(allPlayersItem) {
            menuNavigator.openMenu(menuFactory.createClaimPlayerMenu(menuNavigator, player, claim)) }
        controlsPane.addItem(guiAllPlayersItem, 4, 0)

        // Add list of players
        val warpsPane = StaticPane(0, 2, 9, 4)
        gui.addPane(warpsPane)
        var xSlot = 0
        var ySlot = 0
        for (trustedPlayer in trustedPlayers) {
            val targetPlayer = Bukkit.getOfflinePlayer(trustedPlayer)
            val playerPermissions = getClaimPlayerPermissions.execute(claim.id, trustedPlayer)
            val warpItem = createHead(targetPlayer)
                .name("${targetPlayer.name}")
                .lore(lang.legacy("menu.trusted_players.item.has_permission.lore", "permission_count" to playerPermissions.count()))
            val guiWarpItem = GuiItem(warpItem) {
                menuNavigator.openMenu(menuFactory.createClaimPlayerPermissionsMenu(menuNavigator, player, claim, targetPlayer)) }
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
            .name(lang.legacy("menu.common.item.back.name"))

        val guiExitItem = GuiItem(exitItem) { backButtonAction() }
        controlsPane.addItem(guiExitItem, 0, 0)
        return controlsPane
    }

    private fun addPaginator(playerId: UUID, controlsPane: StaticPane, currentPage: Int, totalPages: Int) {
        // Add prev item
        val prevItem = ItemStack.of(Material.ARROW)
            .name(lang.legacy("menu.common.item.prev.name"))
        val guiPrevItem = GuiItem(prevItem) { guiEvent -> guiEvent.isCancelled = true }
        controlsPane.addItem(guiPrevItem, 6, 0)

        // Add page item
        val pageItem = ItemStack.of(Material.PAPER)
            .name(lang.legacy("menu.common.item.page.name", "current_page" to currentPage, "total_pages" to totalPages))
        val guiPageItem = GuiItem(pageItem) { guiEvent -> guiEvent.isCancelled = true }
        controlsPane.addItem(guiPageItem, 7, 0)

        // Add next item
        val nextItem = ItemStack.of(Material.ARROW)
            .name(lang.legacy("menu.common.item.next.name"))
        val guiNextItem = GuiItem(nextItem) { guiEvent -> guiEvent.isCancelled = true }
        controlsPane.addItem(guiNextItem, 8, 0)
    }
}
