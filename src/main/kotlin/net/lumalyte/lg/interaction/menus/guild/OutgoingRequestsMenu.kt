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

class OutgoingRequestsMenu(
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
        val gui = ChestGui(6, "§6Outgoing Relation Requests")
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
        val outgoingRequests = relationService.getOutgoingRequests(guild.id)
            .sortedByDescending { it.createdAt }

        // Calculate pagination
        val totalPages = (outgoingRequests.size + itemsPerPage - 1) / itemsPerPage
        if (currentPage >= totalPages && totalPages > 0) {
            currentPage = totalPages - 1
        }

        // Get requests for current page
        val startIndex = currentPage * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, outgoingRequests.size)
        val pageRequests = outgoingRequests.toList().subList(startIndex, endIndex)

        // Clear existing panes
        requestsPane.clear()

        val newPage = StaticPane(0, 0, 7, 4)

        if (pageRequests.isEmpty()) {
            // No requests - show empty message
            val emptyItem = ItemStack(Material.BARRIER)
                .name("§7No Outgoing Requests")
                .lore("§7You haven't sent any relation requests.")
                .lore("§7Use the diplomatic actions to send")
                .lore("§7alliance or truce requests to other guilds.")

            val guiItem = GuiItem(emptyItem) { }
            newPage.addItem(guiItem, 3, 1)
        } else {
            // Add request items to the page
            for ((index, relation) in pageRequests.withIndex()) {
                val x = index % 7
                val y = index / 7
                val requestItem = createRequestItem(relation)
                val guiItem = GuiItem(requestItem) {
                    openCancelConfirmMenu(relation)
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
            .lore("§7To: §f$guildName")
            .lore("§7Members: §f$memberCount")
            .lore("§7Sent: §f$timeAgo")

        // Add truce duration if applicable
        if (relation.type == RelationType.TRUCE && relation.expiresAt != null) {
            val durationDays = Duration.between(relation.createdAt, relation.expiresAt).toDays()
            item.lore("§7Duration: §f$durationDays days")
        }

        item.lore("§7")
            .lore("§e⏱ Awaiting response...")
            .lore("§cClick to cancel request")

        return item
    }

    private fun openCancelConfirmMenu(relation: Relation) {
        // Create a small menu with confirm cancel
        val gui = ChestGui(3, "§6Cancel Request?")
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

        // Confirm cancel button
        val cancelItem = ItemStack(Material.RED_CONCRETE)
            .name("§c✗ Cancel Request")
            .lore("§7Cancel your request to")
            .lore("§f$guildName")
            .lore("§7")
            .lore("§cThis action cannot be undone.")

        val cancelGuiItem = GuiItem(cancelItem) {
            cancelRequest(relation, guildName)
        }
        pane.addItem(cancelGuiItem, 3, 1)

        // Back button
        val backItem = ItemStack(Material.ARROW)
            .name("§eBack")
            .lore("§7Return to requests list")

        val backGuiItem = GuiItem(backItem) {
            open()
        }
        pane.addItem(backGuiItem, 5, 1)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun cancelRequest(relation: Relation, guildName: String) {
        val success = relationService.cancelRequest(relation.id, guild.id, player.uniqueId)

        if (success) {
            val typeName = when (relation.type) {
                RelationType.ALLY -> "alliance request"
                RelationType.TRUCE -> "truce request"
                RelationType.NEUTRAL -> "peace request"
                else -> "request"
            }

            player.sendMessage("§c✗ You cancelled the $typeName to $guildName.")
            player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 0.8f)

            // Notify the other guild
            notifyGuildMembers(relation.getOtherGuild(guild.id), "§7${guild.name} §7has cancelled their $typeName.")

            // Refresh the menu
            open()
        } else {
            player.sendMessage("§c✗ Failed to cancel request. It may have already been accepted or rejected.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            open()
        }
    }

    private fun addNavigationButtons(pane: StaticPane) {
        val allRequests = relationService.getOutgoingRequests(guild.id)
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
            .name("§ePage ${currentPage + 1} / ${if (totalPages > 0) totalPages else 1}")
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
