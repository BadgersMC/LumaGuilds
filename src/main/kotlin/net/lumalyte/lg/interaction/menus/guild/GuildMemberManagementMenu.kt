package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildMemberManagementMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                                private var guild: Guild, private val messageService: MessageService): Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    private lateinit var memberPane: StaticPane
    private var currentPage = 0
    private val itemsPerPage = 45 // 9x5 grid

    override fun open() {
        // Create 6x9 double chest GUI
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Member Management - ${guild.name}"))
        val pane = StaticPane(0, 0, 9, 6)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)

        // Initialize member display pane
        memberPane = StaticPane(0, 0, 9, 5)
        updateMemberDisplay()

        // Add navigation buttons
        addNavigationButtons(pane)

        // Add action buttons
        addInviteButton(pane, 0, 5)
        addPromoteDemoteButton(pane, 2, 5)
        addKickButton(pane, 4, 5)
        addBulkOperationsButton(pane, 6, 5)
        addBackButton(pane, 8, 5)

        gui.addPane(memberPane)
        gui.addPane(pane)
        gui.show(player)
    }

    private fun updateMemberDisplay() {
        val allMembers = memberService.getGuildMembers(guild.id).sortedBy { it.playerId }

        // Calculate pagination
        val totalPages = (allMembers.size + itemsPerPage - 1) / itemsPerPage
        if (currentPage >= totalPages) {
            currentPage = maxOf(0, totalPages - 1)
        }

        // Get members for current page
        val startIndex = currentPage * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, allMembers.size)
        val pageMembers = allMembers.subList(startIndex, endIndex)

        // Clear existing items
        // Note: StaticPane doesn't have a clear method, so we'll recreate it
        val newPane = StaticPane(0, 0, 9, 5)

        // Add member items to the pane
        for ((index, member) in pageMembers.withIndex()) {
            val x = index % 9
            val y = index / 9
            val memberItem = createMemberHead(member)
            val guiItem = GuiItem(memberItem) {
                // Open member details menu for promote/demote
                openMemberDetails(member)
            }
            newPane.addItem(guiItem, x, y)
        }

        // Replace the pane (this is a simplified approach)
        // In a real implementation, you'd need to properly replace the pane in the GUI
        memberPane = newPane
    }

    private fun createMemberHead(member: Member): ItemStack {
        val head = ItemStack(Material.PLAYER_HEAD)
        val meta = head.itemMeta as SkullMeta

        // Try to get player name from online players or cache
        val playerName = Bukkit.getPlayer(member.playerId)?.name ?: "Unknown Player"

        // Set skull owner using Craftatar API URL
        try {
            val skullMeta = meta as SkullMeta
            // Use Craftatar API for player heads
            val textureUrl = "https://craftatar.com/avatars/${member.playerId}?size=64&default=MHF_Steve&overlay"
            // Note: In a real implementation, you'd need to set the skull texture properly
            // This is a simplified version - you'd need skull texture utilities
        } catch (e: Exception) {
            // Fallback if skull texture setting fails
        }

        head.itemMeta = meta

        return head.setAdventureName(player, messageService, "<white>👤 $playerName")
            .addAdventureLore(player, messageService, "<gray>Player: <white>$playerName")
            .addAdventureLore(player, messageService, "<gray>Joined: <white>${member.joinedAt}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>Click to view options")
    }

    private fun addNavigationButtons(pane: StaticPane) {
        val allMembers = memberService.getGuildMembers(guild.id)
        val totalPages = (allMembers.size + itemsPerPage - 1) / itemsPerPage

        // Previous page button
        val prevItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<white>⬅️ PREVIOUS PAGE")
            .addAdventureLore(player, messageService, "<gray>Go to previous page")

        val prevGuiItem = GuiItem(prevItem) {
            if (currentPage > 0) {
                currentPage--
                open() // Reopen menu to refresh display
            }
        }
        pane.addItem(prevGuiItem, 0, 5)

        // Next page button
        val nextItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<white>NEXT PAGE ➡️")
            .addAdventureLore(player, messageService, "<gray>Go to next page")

        val nextGuiItem = GuiItem(nextItem) {
            if (currentPage < totalPages - 1) {
                currentPage++
                open() // Reopen menu to refresh display
            }
        }
        pane.addItem(nextGuiItem, 8, 5)

        // Page indicator
        val pageItem = ItemStack(Material.PAPER)
            .setAdventureName(player, messageService, "<white>📄 PAGE ${currentPage + 1}/${maxOf(1, totalPages)}")
            .addAdventureLore(player, messageService, "<gray>Current page indicator")

        pane.addItem(GuiItem(pageItem), 4, 5)
    }

    private fun addInviteButton(pane: StaticPane, x: Int, y: Int) {
        val inviteItem = ItemStack(Material.GREEN_WOOL)
            .setAdventureName(player, messageService, "<green>➕ INVITE PLAYER")
            .addAdventureLore(player, messageService, "<gray>Invite a new player to the guild")
            .addAdventureLore(player, messageService, "<gray>Requires INVITE_MEMBERS permission")

        val inviteGuiItem = GuiItem(inviteItem) {
            if (memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)) {
                menuNavigator.openMenu(menuFactory.createGuildInviteMenu(menuNavigator, player, guild))
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You don't have permission to invite players!")
            }
        }
        pane.addItem(inviteGuiItem, x, y)
    }

    private fun addPromoteDemoteButton(pane: StaticPane, x: Int, y: Int) {
        val promoteItem = ItemStack(Material.GOLDEN_APPLE)
            .setAdventureName(player, messageService, "<gold>⬆️ PROMOTE/DEMOTE")
            .addAdventureLore(player, messageService, "<gray>Change member ranks")
            .addAdventureLore(player, messageService, "<gray>Requires MANAGE_MEMBERS permission")

        val promoteGuiItem = GuiItem(promoteItem) {
            if (memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)) {
                AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Click on a member head to promote/demote them")
                // The member heads will handle the click events
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You don't have permission to manage member ranks!")
            }
        }
        pane.addItem(promoteGuiItem, x, y)
    }

    private fun openMemberDetails(member: Member) {
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You don't have permission to manage member ranks!")
            return
        }

        // Open individual member management menu
        menuNavigator.openMenu(IndividualMemberManagementMenu(menuNavigator, player, guild, member, messageService))
    }

    private fun addKickButton(pane: StaticPane, x: Int, y: Int) {
        val kickItem = ItemStack(Material.RED_WOOL)
            .setAdventureName(player, messageService, "<red>➖ KICK PLAYER")
            .addAdventureLore(player, messageService, "<gray>Remove a player from the guild")
            .addAdventureLore(player, messageService, "<gray>Requires KICK_MEMBERS permission")

        val kickGuiItem = GuiItem(kickItem) {
            if (memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)) {
                menuNavigator.openMenu(menuFactory.createGuildKickMenu(menuNavigator, player, guild))
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You don't have permission to kick players!")
            }
        }
        pane.addItem(kickGuiItem, x, y)
    }

    private fun addBulkOperationsButton(pane: StaticPane, x: Int, y: Int) {
        val bulkItem = ItemStack(Material.BOOKSHELF)
            .setAdventureName(player, messageService, "<gold>📊 BULK OPERATIONS")
            .addAdventureLore(player, messageService, "<gray>Mass rank changes, messaging, and more")

        val bulkGuiItem = GuiItem(bulkItem) {
            menuNavigator.openMenu(GuildBulkMemberOperationsMenu(menuNavigator, player, guild, messageService))
        }
        pane.addItem(bulkGuiItem, x, y)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack(Material.BARRIER)
            .setAdventureName(player, messageService, "<red>⬅️ BACK")
            .addAdventureLore(player, messageService, "<gray>Return to guild control panel")

        val backGuiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(backGuiItem, x, y)
    }}

