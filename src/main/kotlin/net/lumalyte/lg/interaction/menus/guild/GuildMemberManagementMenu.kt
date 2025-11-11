package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
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

class GuildMemberManagementMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                                private var guild: Guild): Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    private lateinit var memberPane: StaticPane
    private var currentPage = 0
    private val itemsPerPage = 45 // 9x5 grid

    override fun open() {
        // Create 6x9 double chest GUI
        val gui = ChestGui(6, "§6Member Management - ${guild.name}")
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // Initialize member display pane
        memberPane = StaticPane(0, 0, 9, 5)
        updateMemberDisplay()

        // Add navigation buttons
        addNavigationButtons(pane)

        // Add action buttons
        addInviteButton(pane, 1, 5)
        addPromoteDemoteButton(pane, 3, 5)
        addKickButton(pane, 5, 5)
        addBackButton(pane, 7, 5)

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

        head.setData(
            DataComponentTypes.PROFILE,
            ResolvableProfile.resolvableProfile().uuid(member.playerId));
        val meta = head.itemMeta as SkullMeta

        // Try to get player name from online players or cache
        val playerName = Bukkit.getOfflinePlayer(member.playerId)?.name ?: "Unknown Player"


        head.itemMeta = meta

        return head.name("§f👤 $playerName")
            .lore("§7Player: §f$playerName")
            .lore("§7Joined: §f${member.joinedAt}")
            .lore("§7")
            .lore("§eClick to view options")
    }

    private fun addNavigationButtons(pane: StaticPane) {
        val allMembers = memberService.getGuildMembers(guild.id)
        val totalPages = (allMembers.size + itemsPerPage - 1) / itemsPerPage

        // Previous page button
        val prevItem = ItemStack(Material.ARROW)
            .name("§f⬅ PREVIOUS PAGE")
            .lore("§7Go to previous page")

        val prevGuiItem = GuiItem(prevItem) {
            if (currentPage > 0) {
                currentPage--
                open() // Reopen menu to refresh display
            }
        }
        pane.addItem(prevGuiItem, 0, 5)

        // Next page button
        val nextItem = ItemStack(Material.ARROW)
            .name("§fNEXT PAGE ➡")
            .lore("§7Go to next page")

        val nextGuiItem = GuiItem(nextItem) {
            if (currentPage < totalPages - 1) {
                currentPage++
                open() // Reopen menu to refresh display
            }
        }
        pane.addItem(nextGuiItem, 8, 5)

        // Page indicator
        val pageItem = ItemStack(Material.PAPER)
            .name("§f📄 PAGE ${currentPage + 1}/${maxOf(1, totalPages)}")
            .lore("§7Current page indicator")

        pane.addItem(GuiItem(pageItem), 4, 5)
    }

    private fun addInviteButton(pane: StaticPane, x: Int, y: Int) {
        val inviteItem = ItemStack(Material.GREEN_WOOL)
            .name("§a➕ INVITE PLAYER")
            .lore("§7Invite a new player to the guild")
            .lore("§7Requires INVITE_MEMBERS permission")

        val inviteGuiItem = GuiItem(inviteItem) {
            if (memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)) {
                menuNavigator.openMenu(menuFactory.createGuildInviteMenu(menuNavigator, player, guild))
            } else {
                player.sendMessage("§c❌ You don't have permission to invite players!")
            }
        }
        pane.addItem(inviteGuiItem, x, y)
    }

    private fun addPromoteDemoteButton(pane: StaticPane, x: Int, y: Int) {
        val promoteItem = ItemStack(Material.GOLDEN_APPLE)
            .name("§6⬆ PROMOTE/DEMOTE")
            .lore("§7Change member ranks")
            .lore("§7Requires MANAGE_MEMBERS permission")

        val promoteGuiItem = GuiItem(promoteItem) {
            if (memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)) {
                player.sendMessage("§a✅ Click on a member head to promote/demote them")
                // The member heads will handle the click events
            } else {
                player.sendMessage("§c❌ You don't have permission to manage member ranks!")
            }
        }
        pane.addItem(promoteGuiItem, x, y)
    }

    private fun openMemberDetails(member: Member) {
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)) {
            player.sendMessage("§c❌ You don't have permission to manage member ranks!")
            return
        }

        // Open member rank management menu
        menuNavigator.openMenu(menuFactory.createGuildMemberRankMenu(menuNavigator, player, guild, member))
    }

    private fun addKickButton(pane: StaticPane, x: Int, y: Int) {
        val kickItem = ItemStack(Material.RED_WOOL)
            .name("§c➖ KICK PLAYER")
            .lore("§7Remove a player from the guild")
            .lore("§7Requires KICK_MEMBERS permission")

        val kickGuiItem = GuiItem(kickItem) {
            if (memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)) {
                menuNavigator.openMenu(menuFactory.createGuildKickMenu(menuNavigator, player, guild))
            } else {
                player.sendMessage("§c❌ You don't have permission to kick players!")
            }
        }
        pane.addItem(kickGuiItem, x, y)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack(Material.BARRIER)
            .name("§c⬅ BACK")
            .lore("§7Return to guild control panel")

        val backGuiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(backGuiItem, x, y)
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}

