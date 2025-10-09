package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
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
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildKickMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                   private var guild: Guild, private val messageService: MessageService): Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    private lateinit var memberPane: StaticPane
    private var currentPage = 0
    private val itemsPerPage = 45 // 9x5 grid

    override fun open() {
        // Create 6x9 double chest GUI
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Kick Member - ${guild.name}"))
        val pane = StaticPane(0, 0, 9, 6)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)

        // Initialize member display pane
        memberPane = StaticPane(0, 0, 9, 5)
        updateMemberDisplay()

        // Add navigation buttons
        addNavigationButtons(pane)

        // Add back button
        addBackButton(pane, 4, 5)

        gui.addPane(memberPane)
        gui.addPane(pane)
        gui.show(player)
    }

    private fun updateMemberDisplay() {
        val allMembers = memberService.getGuildMembers(guild.id)
            .filter { it.playerId != player.uniqueId } // Can't kick yourself
            .sortedBy { it.playerId }

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
        val newPane = StaticPane(0, 0, 9, 5)

        // Add member items to the pane
        for ((index, member) in pageMembers.withIndex()) {
            val x = index % 9
            val y = index / 9
            val memberItem = createMemberKickItem(member)
            val guiItem = GuiItem(memberItem) {
                kickMember(member)
            }
            newPane.addItem(guiItem, x, y)
        }

        // Replace the pane
        memberPane = newPane
    }

    private fun createMemberKickItem(member: Member): ItemStack {
        val head = ItemStack(Material.PLAYER_HEAD)
        val meta = head.itemMeta as SkullMeta

        // Try to get player name from online players
        val playerName = Bukkit.getPlayer(member.playerId)?.name ?: "Unknown Player"

        // Set skull owner
        try {
            val skullMeta = meta as SkullMeta
            val onlinePlayer = Bukkit.getPlayer(member.playerId)
            if (onlinePlayer != null) {
                skullMeta.owningPlayer = onlinePlayer
            }
        } catch (e: Exception) {
            // Fallback if skull texture setting fails
        }

        head.itemMeta = meta

        return head.setAdventureName(player, messageService, "<red>👤 $playerName")
            .addAdventureLore(player, messageService, "<gray>Player: <white>$playerName")
            .addAdventureLore(player, messageService, "<gray>Joined: <white>${member.joinedAt}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<red>Click to kick from guild")
            .addAdventureLore(player, messageService, "<red>⚠️ This cannot be undone!")
    }

    private fun kickMember(member: Member) {
        // Show confirmation menu instead of directly kicking
        val menuFactory = MenuFactory()
        menuNavigator.openMenu(menuFactory.createGuildKickConfirmationMenu(menuNavigator, player, guild, member))
    }

    private fun addNavigationButtons(pane: StaticPane) {
        val allMembers = memberService.getGuildMembers(guild.id)
            .filter { it.playerId != player.uniqueId }
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

        pane.addItem(GuiItem(pageItem), 2, 5)
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

