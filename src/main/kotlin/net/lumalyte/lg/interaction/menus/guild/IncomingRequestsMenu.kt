package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Relation
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.time.Instant
import java.util.*

class IncomingRequestsMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val relationService: RelationService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    private lateinit var requestsPane: PaginatedPane
    private var currentPage = 0
    private val itemsPerPage = 28 // 4 rows x 7 columns

    override fun open() {
        val gui = ChestGui(6, "§6Incoming Relation Requests")
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // Initialize requests display pane
        requestsPane = PaginatedPane(1, 0, 7, 4)
        updateRequestsDisplay()

        // Add navigation buttons
        addNavigationButtons(pane)

        // Add back button
        addBackButton(pane, 4, 5)

        gui.addPane(requestsPane)
        gui.addPane(pane)
        gui.show(player)
    }

    private fun updateRequestsDisplay() {
        val incomingRequests = relationService.getIncomingRequests(guild.id)
            .sortedByDescending { it.createdAt }

        // Calculate pagination
        val totalPages = (incomingRequests.size + itemsPerPage - 1) / itemsPerPage
        if (currentPage >= totalPages && totalPages > 0) {
            currentPage = totalPages - 1
        }

        // Get requests for current page
        val startIndex = currentPage * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, incomingRequests.size)
        val pageRequests = incomingRequests.toList().subList(startIndex, endIndex)

        // Clear existing panes
        requestsPane.clear()

        val newPage = StaticPane(0, 0, 7, 4)

        if (pageRequests.isEmpty()) {
            // No requests - show empty message
            val emptyItem = ItemStack(Material.BARRIER)
                .name("§7No Incoming Requests")
                .lore("§7You don't have any pending relation requests.")
                .lore("§7When other guilds send you alliance or")
                .lore("§7truce requests, they will appear here.")

            val guiItem = GuiItem(emptyItem) { }
            newPage.addItem(guiItem, 3, 1)
        } else {
            // Add request items to the page
            for ((index, relation) in pageRequests.withIndex()) {
                val x = index % 7
                val y = index / 7
                val requestItem = createRequestItem(relation)
                val guiItem = GuiItem(requestItem) {
                    openRequestActionMenu(relation)
                }
                newPage.addItem(guiItem, x, y)
            }
        }

        requestsPane.addPage(newPage)
        requestsPane.page = 0
    }

    private fun createRequestItem(relation: Relation): ItemStack {
        // Get the other guild
        val otherGuildId = relation.getOtherGuild(guild.id)
        val otherGuild = guildService.getGuild(otherGuildId)

        val guildName = otherGuild?.name ?: "Unknown Guild"
        val memberCount = otherGuild?.let { memberService.getMemberCount(it.id) } ?: 0

        // Determine icon and type text based on relation type
        val (material, typeName, typeColor) = when (relation.type) {
            RelationType.ALLY -> Triple(Material.GOLDEN_APPLE, "Alliance Request", "§a")
            RelationType.TRUCE -> Triple(Material.WHITE_BANNER, "Truce Request", "§e")
            RelationType.NEUTRAL -> Triple(Material.PAPER, "Peace Request", "§f")
            else -> Triple(Material.PAPER, "Unknown Request", "§7")
        }

        // Calculate time ago
        val timeAgo = formatTimeAgo(relation.createdAt)

        val item = ItemStack(material)
            .name("$typeColor$typeName")
            .lore("§7From: §f$guildName")
            .lore("§7Members: §f$memberCount")
            .lore("§7Received: §f$timeAgo")

        // Add truce duration if applicable
        if (relation.type == RelationType.TRUCE && relation.expiresAt != null) {
            val durationDays = Duration.between(relation.createdAt, relation.expiresAt).toDays()
            item.lore("§7Duration: §f$durationDays days")
        }

        item.lore("§7")
            .lore("§aClick to respond")

        return item
    }

    private fun openRequestActionMenu(relation: Relation) {
        // Create a small menu with accept/reject options
        val gui = ChestGui(3, "§6Accept or Reject?")
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        val otherGuildId = relation.getOtherGuild(guild.id)
        val otherGuild = guildService.getGuild(otherGuildId)
        val guildName = otherGuild?.name ?: "Unknown Guild"

        // Accept button
        val acceptItem = ItemStack(Material.LIME_CONCRETE)
            .name("§a✓ Accept Request")
            .lore("§7Accept the request from")
            .lore("§f$guildName")

        val acceptGuiItem = GuiItem(acceptItem) {
            acceptRequest(relation, guildName)
        }
        pane.addItem(acceptGuiItem, 2, 1)

        // Reject button
        val rejectItem = ItemStack(Material.RED_CONCRETE)
            .name("§c✗ Reject Request")
            .lore("§7Reject the request from")
            .lore("§f$guildName")

        val rejectGuiItem = GuiItem(rejectItem) {
            rejectRequest(relation, guildName)
        }
        pane.addItem(rejectGuiItem, 6, 1)

        // Back button
        val backItem = ItemStack(Material.ARROW)
            .name("§eBack")
            .lore("§7Return to requests list")

        val backGuiItem = GuiItem(backItem) {
            open()
        }
        pane.addItem(backGuiItem, 4, 2)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun acceptRequest(relation: Relation, guildName: String) {
        val result = when (relation.type) {
            RelationType.ALLY -> relationService.acceptAlliance(relation.id, guild.id, player.uniqueId)
            RelationType.TRUCE -> relationService.acceptTruce(relation.id, guild.id, player.uniqueId)
            RelationType.NEUTRAL -> relationService.acceptUnenemy(relation.id, guild.id, player.uniqueId)
            else -> null
        }

        if (result != null) {
            val typeName = when (relation.type) {
                RelationType.ALLY -> "alliance"
                RelationType.TRUCE -> "truce"
                RelationType.NEUTRAL -> "peace agreement"
                else -> "request"
            }

            player.sendMessage("§a✓ You accepted the $typeName with $guildName!")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f)

            // Notify the other guild
            notifyGuildMembers(relation.getOtherGuild(guild.id), "§a${guild.name} §7has accepted your $typeName request!")

            // Refresh the menu
            open()
        } else {
            player.sendMessage("§c✗ Failed to accept request. It may have expired or been cancelled.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            open()
        }
    }

    private fun rejectRequest(relation: Relation, guildName: String) {
        val success = relationService.rejectRequest(relation.id, guild.id, player.uniqueId)

        if (success) {
            val typeName = when (relation.type) {
                RelationType.ALLY -> "alliance request"
                RelationType.TRUCE -> "truce request"
                RelationType.NEUTRAL -> "peace request"
                else -> "request"
            }

            player.sendMessage("§c✗ You rejected the $typeName from $guildName.")
            player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 0.8f)

            // Notify the other guild
            notifyGuildMembers(relation.getOtherGuild(guild.id), "§c${guild.name} §7has rejected your $typeName.")

            // Refresh the menu
            open()
        } else {
            player.sendMessage("§c✗ Failed to reject request.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            open()
        }
    }

    private fun addNavigationButtons(pane: StaticPane) {
        val allRequests = relationService.getIncomingRequests(guild.id)
        val totalPages = (allRequests.size + itemsPerPage - 1) / itemsPerPage

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
            .name("§ePage ${currentPage + 1} / $totalPages")
            .lore("§7Total requests: §f${allRequests.size}")

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

    private fun formatTimeAgo(instant: Instant): String {
        val duration = Duration.between(instant, Instant.now())
        return when {
            duration.toMinutes() < 1 -> "Just now"
            duration.toHours() < 1 -> "${duration.toMinutes()}m ago"
            duration.toDays() < 1 -> "${duration.toHours()}h ago"
            duration.toDays() < 7 -> "${duration.toDays()}d ago"
            else -> "${duration.toDays() / 7}w ago"
        }
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
