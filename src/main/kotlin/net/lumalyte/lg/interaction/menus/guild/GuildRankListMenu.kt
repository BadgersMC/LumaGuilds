package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildRankListMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                       private var guild: Guild, private val messageService: MessageService): Menu, KoinComponent {

    private val menuFactory: MenuFactory by inject()
    private val rankService: RankService by inject()
    private val memberService: MemberService by inject()
    private val guildService: GuildService by inject()

    private var currentPage = 0
    private val ranksPerPage = 8

    override fun open() {
        // Check permissions - anyone in guild can view rank list
        if (!memberService.isPlayerInGuild(player.uniqueId, guild.id)) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You are not a member of this guild!")
            player.closeInventory()
            return
        }

        // Load and display ranks
        displayRankList()
    }

    private fun displayRankList() {
        val allRanks = rankService.listRanks(guild.id).sortedBy { it.priority }
        val totalPages = maxOf(1, (allRanks.size + ranksPerPage - 1) / ranksPerPage)
        val startIndex = currentPage * ranksPerPage
        val endIndex = minOf(startIndex + ranksPerPage, allRanks.size)

        // Create 6x9 rank list GUI
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold>${guild.name} - Ranks (${allRanks.size})"))

        val pane = StaticPane(0, 0, 9, 6)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)
        gui.addPane(pane)

        // Row 0: Header
        addHeader(pane, allRanks.size)

        // Rows 1-4: Rank list (up to 8 ranks per page)
        addRankList(pane, allRanks.subList(startIndex, endIndex), startIndex)

        // Row 5: Navigation and actions
        addNavigationBar(pane, totalPages, allRanks.size)

        gui.show(player)
    }

    private fun addHeader(pane: StaticPane, totalRanks: Int) {
        val headerItem = ItemStack(Material.BOOKSHELF)
            .setAdventureName(player, messageService, "<gold>Guild Rank Hierarchy")
            .lore(
                "<gray>Total Ranks: <white>$totalRanks",
                "<gray>Page: <white>${currentPage + 1}",
                "",
                "<gray>Higher levels = more permissions"
            )
        pane.addItem(GuiItem(headerItem), 4, 0)
    }

    private fun addRankList(pane: StaticPane, ranks: List<Rank>, startIndex: Int) {
        for ((index, rank) in ranks.withIndex()) {
            val slotIndex = index
            val row = 1 + (slotIndex / 4)
            val col = (slotIndex % 4) * 2 + 1

            addRankItem(pane, rank, col, row)
        }
    }

    private fun addRankItem(pane: StaticPane, rank: Rank, x: Int, y: Int) {
        val memberCount = memberService.getMembersByRank(guild.id, rank.id).size
        val canManage = canManageRank(rank)

        val rankItem = ItemStack(Material.PAPER)
            .setAdventureName(player, messageService, "<white>${rank.name}")
            .lore(listOf(
                "<gray>Level: <white>${rank.priority}",
                "<gray>Members: <white>$memberCount",
                "<gray>Permissions: <white>${rank.permissions.size}",
                ""
            ) + if (rank.permissions.isNotEmpty()) {
                listOf("<gray>Key Permissions:") +
                rank.permissions.take(3).map { perm ->
                    "<dark_gray>• <gray>${perm.name.lowercase().replace("_", " ")}"
                } + if (rank.permissions.size > 3) {
                    listOf("<dark_gray>• <gray>... and ${rank.permissions.size - 3} more", "")
                } else {
                    listOf("")
                }
            } else {
                listOf("")
            } + if (canManage) {
                listOf("<yellow>Click to manage this rank")
            } else {
                listOf("<gray>Click to view details")
            })

        pane.addItem(GuiItem(rankItem) { event ->
            event.isCancelled = true
            if (canManage) {
                openRankManagement(rank)
            } else {
                showRankDetails(rank)
            }
        }, x, y)
    }

    private fun addNavigationBar(pane: StaticPane, totalPages: Int, totalRanks: Int) {
        // Left side: Navigation
        if (currentPage > 0) {
            val prevItem = ItemStack(Material.ARROW)
                .setAdventureName(player, messageService, "<yellow>◀ Previous Page")
                .addAdventureLore(player, messageService, "<gray>Go to page ${currentPage}")
            pane.addItem(GuiItem(prevItem) { event ->
                event.isCancelled = true
                currentPage--
                displayRankList()
            }, 1, 5)
        }

        // Center: Page info and refresh
        val pageItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<gold>Page ${currentPage + 1} of $totalPages")
            .lore(
                "<gray>Showing ${minOf((currentPage + 1) * ranksPerPage, totalRanks)} of $totalRanks ranks",
                "<gray>Click to refresh"
            )
        pane.addItem(GuiItem(pageItem) { event ->
            event.isCancelled = true
            displayRankList() // Refresh
        }, 4, 5)

        if (currentPage < totalPages - 1) {
            val nextItem = ItemStack(Material.ARROW)
                .setAdventureName(player, messageService, "<yellow>Next Page ▶")
                .addAdventureLore(player, messageService, "<gray>Go to page ${currentPage + 2}")
            pane.addItem(GuiItem(nextItem) { event ->
                event.isCancelled = true
                currentPage++
                displayRankList()
            }, 7, 5)
        }

        // Right side: Actions (if player has permissions)
        if (guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RANKS)) {
            val createItem = ItemStack(Material.ANVIL)
                .setAdventureName(player, messageService, "<green>Create Rank")
                .addAdventureLore(player, messageService, "<gray>Create a new rank")
            pane.addItem(GuiItem(createItem) { event ->
                event.isCancelled = true
                openRankCreation()
            }, 6, 5)

            val manageItem = ItemStack(Material.COMMAND_BLOCK)
                .setAdventureName(player, messageService, "<gold>Advanced")
                .addAdventureLore(player, messageService, "<gray>Advanced rank management")
            pane.addItem(GuiItem(manageItem) { event ->
                event.isCancelled = true
                openAdvancedManagement()
            }, 8, 5)
        }

        // Back button (always available)
        val backItem = ItemStack(Material.BARRIER)
            .setAdventureName(player, messageService, "<red>Back to Control Panel")
            .addAdventureLore(player, messageService, "<gray>Return to guild management")
        pane.addItem(GuiItem(backItem) { event ->
            event.isCancelled = true
        menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 0, 5)
    }

    private fun showRankDetails(rank: Rank) {
        // Create a simple details popup (could be expanded to a full menu)
        val detailsMessage = buildString {
            appendLine("<gold>=== ${rank.name} Details ===")
            appendLine("<gray>Level: <white>${rank.priority}")
            appendLine("<gray>Members: <white>${memberService.getMembersByRank(guild.id, rank.id).size}")
            appendLine("<gray>Permissions: <white>${rank.permissions.size}")

            if (rank.permissions.isNotEmpty()) {
                appendLine("")
                appendLine("<gray>Permissions:")
                rank.permissions.forEach { perm ->
                    appendLine("<dark_gray>• <gray>${perm.name.lowercase().replace("_", " ")}")
                }
            }
        }

        player.sendMessage(detailsMessage)
    }

    private fun openRankManagement(rank: Rank) {
        // Open rank edit menu for this specific rank
        menuNavigator.openMenu(menuFactory.createRankEditMenu(menuNavigator, player, guild, rank))
    }

    private fun openRankCreation() {
        // Open rank creation menu
        menuNavigator.openMenu(menuFactory.createRankCreationMenu(menuNavigator, player, guild))
    }

    private fun openAdvancedManagement() {
        // Open the advanced rank management menu
        menuNavigator.openMenu(menuFactory.createGuildRankManagementMenu(menuNavigator, player, guild))
    }

    private fun canManageRank(rank: Rank): Boolean {
        // Check if player can manage this rank
        if (!guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RANKS)) {
            return false
        }

        // Cannot manage ranks higher than or equal to own rank
        val playerRank = rankService.getPlayerRank(player.uniqueId, guild.id) ?: return false
        return rank.priority < playerRank.priority
    }}
