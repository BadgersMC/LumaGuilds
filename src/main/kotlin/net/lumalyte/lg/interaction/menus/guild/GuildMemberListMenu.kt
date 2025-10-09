package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.OfflinePlayer
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

class GuildMemberListMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                         private var guild: Guild, private val messageService: MessageService): Menu, KoinComponent {

    private val menuFactory: MenuFactory by inject()
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()
    private val guildService: GuildService by inject()

    private var currentPage = 0
    private val membersPerPage = 8
    private var allMembers: List<Member> = emptyList()
    private var filteredMembers: List<Member> = emptyList()
    private var searchQuery: String = ""

    override fun open() {
        // Check permissions - anyone in guild can view member list
        if (!memberService.isPlayerInGuild(player.uniqueId, guild.id)) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You are not a member of this guild!")
            player.closeInventory()
            return
        }

        // Load members
        loadMembers()

        // Create 6x9 member list GUI
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold>${guild.name} - Members (${filteredMembers.size})"))

        val pane = StaticPane(0, 0, 9, 6)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)
        gui.addPane(pane)

        // Row 0: Search bar
        addSearchBar(pane)

        // Rows 1-4: Member list (8 members per page)
        addMemberList(pane)

        // Row 5: Navigation and actions
        addNavigationBar(pane)

        gui.show(player)
    }

    private fun loadMembers() {
        try {
            allMembers = memberService.getGuildMembers(guild.id).sortedWith(compareBy(
                { getRankLevel(it) }, // Sort by rank level first
                { it.joinedAt } // Then by join date
            ))

            applySearchFilter()
        } catch (e: Exception) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to load member list. Please try again.")
            e.printStackTrace()
            allMembers = emptyList()
            filteredMembers = emptyList()
        }
    }

    private fun applySearchFilter() {
        filteredMembers = if (searchQuery.isBlank()) {
            allMembers
        } else {
            allMembers.filter { member ->
                val offlinePlayer = Bukkit.getOfflinePlayer(member.playerId)
                offlinePlayer.name?.contains(searchQuery, ignoreCase = true) == true
            }
        }
    }

    private fun addSearchBar(pane: StaticPane) {
        // Search input area (placeholder - would need chat input system for actual search)
        val searchItem = ItemStack(Material.COMPASS)
            .setAdventureName(player, messageService, "<yellow>🔍 Search Members")
            .lore(
                "<gray>Click to search by player name",
                "<gray>Current filter: <white>${if (searchQuery.isBlank()) "None" else searchQuery}",
                "<gray>Showing <white>${filteredMembers.size} <gray>of <white>${allMembers.size} <gray>members"
            )
        pane.addItem(GuiItem(searchItem) { event ->
            event.isCancelled = true
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>Search functionality coming soon! For now, all members are shown.")
        }, 4, 0)
    }

    private fun addMemberList(pane: StaticPane) {
        val startIndex = currentPage * membersPerPage
        val endIndex = minOf(startIndex + membersPerPage, filteredMembers.size)

        for (i in startIndex until endIndex) {
            val member = filteredMembers[i]
            val slotIndex = i - startIndex

            // Calculate grid position (8 members in a 2x4 grid in rows 1-4)
            val row = 1 + (slotIndex / 4)
            val col = (slotIndex % 4) * 2 + 1 // Leave space between items

            addMemberItem(pane, member, col, row)
        }
    }

    private fun addMemberItem(pane: StaticPane, member: Member, x: Int, y: Int) {
        val offlinePlayer = Bukkit.getOfflinePlayer(member.playerId)
        val rank = rankService.getPlayerRank(member.playerId, guild.id)

        val headMaterial = if (offlinePlayer.isOnline) Material.PLAYER_HEAD else Material.SKELETON_SKULL

        val memberItem = ItemStack(headMaterial)
            .name("<white>${offlinePlayer.name ?: "Unknown"}")
            .lore(
                "<gray>Rank: <white>${rank?.name ?: "Unknown"}",
                "<gray>Joined: <white>${member.joinedAt.atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}",
                "<gray>Status: <white>${if (offlinePlayer.isOnline) "§a● Online" else "<gray>● Offline"}",
                "",
                "<gray>Click for management options"
            )

        pane.addItem(GuiItem(memberItem) { event ->
            event.isCancelled = true
            openMemberManagement(member)
        }, x, y)
    }

    private fun addNavigationBar(pane: StaticPane) {
        val totalPages = maxOf(1, (filteredMembers.size + membersPerPage - 1) / membersPerPage)

        // Left side: Navigation
        if (currentPage > 0) {
            val prevItem = ItemStack(Material.ARROW)
                .setAdventureName(player, messageService, "<yellow>◀ Previous Page")
                .addAdventureLore(player, messageService, "<gray>Go to page ${currentPage}")
            pane.addItem(GuiItem(prevItem) { event ->
                event.isCancelled = true
                currentPage--
                open() // Refresh menu
            }, 1, 5)
        }

        // Center: Page info
        val pageItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<gold>Page ${currentPage + 1} of $totalPages")
            .lore(
                "<gray>Showing ${filteredMembers.size} members",
                "<gray>Click to refresh"
            )
        pane.addItem(GuiItem(pageItem) { event ->
            event.isCancelled = true
            loadMembers() // Refresh data
            open() // Refresh menu
        }, 4, 5)

        if (currentPage < totalPages - 1) {
            val nextItem = ItemStack(Material.ARROW)
                .setAdventureName(player, messageService, "<yellow>Next Page ▶")
                .addAdventureLore(player, messageService, "<gray>Go to page ${currentPage + 2}")
            pane.addItem(GuiItem(nextItem) { event ->
                event.isCancelled = true
                currentPage++
                open() // Refresh menu
            }, 7, 5)
        }

        // Right side: Actions (if player has permissions)
        if (guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)) {
            val inviteItem = ItemStack(Material.EMERALD)
                .setAdventureName(player, messageService, "<green>Invite Player")
                .addAdventureLore(player, messageService, "<gray>Invite a new member")
            pane.addItem(GuiItem(inviteItem) { event ->
                event.isCancelled = true
                openInviteMenu()
            }, 6, 5)

            val manageItem = ItemStack(Material.COMMAND_BLOCK)
                .setAdventureName(player, messageService, "<gold>Bulk Manage")
                .addAdventureLore(player, messageService, "<gray>Manage multiple members")
            pane.addItem(GuiItem(manageItem) { event ->
                event.isCancelled = true
                openBulkManagementMenu()
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

    private fun openMemberManagement(member: Member) {
        // Check if player can manage this member
        if (!canManageMember(member)) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You don't have permission to manage this member!")
            return
        }

        // Open member management menu (would need to implement this)
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Member management for ${Bukkit.getOfflinePlayer(member.playerId).name} coming soon!")
    }

    private fun openInviteMenu() {
        // Open guild invite menu
        menuNavigator.openMenu(menuFactory.createGuildInviteMenu(menuNavigator, player, guild))
    }

    private fun openBulkManagementMenu() {
        // Open general member management menu for bulk operations
        menuNavigator.openMenu(menuFactory.createGuildMemberManagementMenu(menuNavigator, player, guild))
    }

    private fun canManageMember(member: Member): Boolean {
        // Check if player can manage this specific member
        if (!guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)) {
            return false
        }

        // Cannot manage yourself
        if (member.playerId == player.uniqueId) {
            return false
        }

        // Cannot manage owner (highest priority rank)
        val highestRank = rankService.listRanks(guild.id).maxByOrNull { it.priority }
        val memberRank = rankService.getPlayerRank(member.playerId, guild.id)
        if (memberRank?.id == highestRank?.id) {
            return false
        }

        // Check rank hierarchy
        val playerRank = rankService.getPlayerRank(player.uniqueId, guild.id)

        return (playerRank?.priority ?: 0) > (memberRank?.priority ?: 0)
    }

    private fun getRankLevel(member: Member): Int {
        return rankService.getPlayerRank(member.playerId, guild.id)?.priority ?: 999
    }}
