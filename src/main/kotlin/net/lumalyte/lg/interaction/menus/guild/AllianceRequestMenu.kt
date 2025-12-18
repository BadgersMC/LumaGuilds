package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import net.lumalyte.lg.utils.deserializeToItemStack
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

class AllianceRequestMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val relationService: RelationService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    private lateinit var guildsPane: PaginatedPane
    private var currentPage = 0
    private val itemsPerPage = 28 // 4 rows x 7 columns

    override fun open() {
        // Check permission
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RELATIONS)) {
            player.sendMessage("§cYou don't have permission to manage relations.")
            return
        }

        val gui = ChestGui(6, "§6Request Alliance")
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // Initialize guilds display pane
        guildsPane = PaginatedPane(1, 0, 7, 4)
        updateGuildsDisplay()

        // Add navigation buttons
        addNavigationButtons(pane)

        // Add back button
        addBackButton(pane, 4, 5)

        gui.addPane(guildsPane)
        gui.addPane(pane)
        gui.show(player)
    }

    private fun updateGuildsDisplay() {
        // Get all guilds that can be allied with
        val allGuilds = guildService.getAllGuilds()
            .filter { it.id != guild.id } // Exclude own guild
            .filter { targetGuild ->
                // Only show neutral guilds (no existing relation)
                relationService.getRelationType(guild.id, targetGuild.id) == RelationType.NEUTRAL
            }
            .filter { targetGuild ->
                // Exclude guilds with pending requests
                val pendingRequests = relationService.getPendingRequests(guild.id)
                !pendingRequests.any { it.getOtherGuild(guild.id) == targetGuild.id }
            }
            .sortedBy { it.name }

        // Calculate pagination
        val totalPages = (allGuilds.size + itemsPerPage - 1) / itemsPerPage
        if (currentPage >= totalPages && totalPages > 0) {
            currentPage = totalPages - 1
        }

        // Get guilds for current page
        val startIndex = currentPage * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, allGuilds.size)
        val pageGuilds = allGuilds.subList(startIndex, endIndex)

        // Clear existing panes
        guildsPane.clear()

        val newPage = StaticPane(0, 0, 7, 4)

        if (pageGuilds.isEmpty()) {
            // No available guilds
            val emptyItem = ItemStack(Material.BARRIER)
                .name("§7No Guilds Available")
                .lore("§7All guilds already have relations")
                .lore("§7with your guild or have pending requests.")

            val guiItem = GuiItem(emptyItem) { }
            newPage.addItem(guiItem, 3, 1)
        } else {
            // Add guild items to the page
            for ((index, targetGuild) in pageGuilds.withIndex()) {
                val x = index % 7
                val y = index / 7
                val guildItem = createGuildItem(targetGuild)
                val guiItem = GuiItem(guildItem) {
                    requestAlliance(targetGuild)
                }
                newPage.addItem(guiItem, x, y)
            }
        }

        guildsPane.addPage(newPage)
        guildsPane.page = 0
    }

    private fun createGuildItem(targetGuild: Guild): ItemStack {
        val memberCount = memberService.getMemberCount(targetGuild.id)

        // Try to use guild banner, fallback to green banner
        val item = if (targetGuild.banner != null) {
            val deserialized = targetGuild.banner.deserializeToItemStack()
            deserialized ?: ItemStack(Material.GREEN_BANNER)
        } else {
            ItemStack(Material.GREEN_BANNER)
        }

        item.name("§a${targetGuild.name}")
            .lore("§7Members: §f$memberCount")
            .lore("§7Level: §f${targetGuild.level}")
            .lore("§7Mode: ${if (targetGuild.mode.name == "PEACEFUL") "§a⚐ Peaceful" else "§c⚔ Hostile"}")
            .lore("§7")
            .lore("§aClick to request alliance")

        return item
    }

    private fun requestAlliance(targetGuild: Guild) {
        val relation = relationService.requestAlliance(guild.id, targetGuild.id, player.uniqueId)

        if (relation != null) {
            player.closeInventory()
            player.sendMessage("§a✓ Alliance request sent to ${targetGuild.name}!")
            player.sendMessage("§7They must accept your request for the alliance to become active.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f)

            // Notify target guild members
            notifyGuildMembers(targetGuild.id, "§6${guild.name} §7has requested an alliance with your guild! Use §6/guild menu §7→ Relations to respond.")
        } else {
            player.sendMessage("§c✗ Failed to send alliance request.")
            player.sendMessage("§7There may already be a pending request.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            open() // Refresh the menu
        }
    }

    private fun addNavigationButtons(pane: StaticPane) {
        val allGuilds = guildService.getAllGuilds()
            .filter { it.id != guild.id }
            .filter { targetGuild ->
                relationService.getRelationType(guild.id, targetGuild.id) == RelationType.NEUTRAL
            }
            .filter { targetGuild ->
                val pendingRequests = relationService.getPendingRequests(guild.id)
                !pendingRequests.any { it.getOtherGuild(guild.id) == targetGuild.id }
            }
        val totalPages = (allGuilds.size + itemsPerPage - 1) / itemsPerPage

        // Previous page button
        if (currentPage > 0) {
            val prevItem = ItemStack(Material.ARROW)
                .name("§f⬅ PREVIOUS PAGE")
                .lore("§7Go to previous page")

            val prevGuiItem = GuiItem(prevItem) {
                currentPage--
                open()
            }
            pane.addItem(prevGuiItem, 0, 4)
        }

        // Page indicator
        val pageItem = ItemStack(Material.PAPER)
            .name("§ePage ${currentPage + 1} / ${if (totalPages > 0) totalPages else 1}")
            .lore("§7Available guilds: §f${allGuilds.size}")

        val pageGuiItem = GuiItem(pageItem) { }
        pane.addItem(pageGuiItem, 4, 4)

        // Next page button
        if (currentPage < totalPages - 1) {
            val nextItem = ItemStack(Material.ARROW)
                .name("§fNEXT PAGE ➡")
                .lore("§7Go to next page")

            val nextGuiItem = GuiItem(nextItem) {
                currentPage++
                open()
            }
            pane.addItem(nextGuiItem, 8, 4)
        }
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack(Material.ARROW)
            .name("§eBack to Relations")
            .lore("§7Return to relations menu")

        val guiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildRelationsMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun notifyGuildMembers(guildId: UUID, message: String) {
        val members = memberService.getGuildMembers(guildId)
        members.forEach { member ->
            val onlinePlayer = Bukkit.getPlayer(member.playerId)
            if (onlinePlayer != null && onlinePlayer.isOnline) {
                onlinePlayer.sendMessage(message)
            }
        }
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
