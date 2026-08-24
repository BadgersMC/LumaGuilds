package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component

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
    private val lang: LangService by inject()

    private lateinit var requestsPane: PaginatedPane
    private var currentPage = 0
    private val itemsPerPage = 28 // 4 rows x 7 columns

    override fun open() {
        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.guiTitle("menu.incoming_requests.title")))
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
            val emptyItem = ItemStack.of(Material.BARRIER)
                .name(lang.gui("menu.incoming_requests.empty.name"))
                .lore(lang.gui("menu.incoming_requests.empty.description"))
                .lore(lang.gui("menu.incoming_requests.empty.hint"))
                .lore(lang.gui("menu.incoming_requests.empty.location"))

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

        val guildName = otherGuild?.name ?: lang.raw("menu.incoming_requests.fallback.unknown_guild")
        val memberCount = otherGuild?.let { memberService.getMemberCount(it.id) } ?: 0

        // Determine icon and type text based on relation type
        val (material, typeName) = when (relation.type) {
            RelationType.ALLY -> Material.GOLDEN_APPLE to lang.gui("menu.incoming_requests.type.alliance.display")
            RelationType.TRUCE -> Material.WHITE_BANNER to lang.gui("menu.incoming_requests.type.truce.display")
            RelationType.NEUTRAL -> Material.PAPER to lang.gui("menu.incoming_requests.type.peace.display")
            else -> Material.PAPER to lang.gui("menu.incoming_requests.type.unknown.display")
        }

        // Calculate time ago
        val timeAgo = formatTimeAgo(relation.createdAt)

        val item = ItemStack.of(material)
            .name(typeName)
            .lore(lang.gui("menu.incoming_requests.request.from", "guild" to guildName))
            .lore(lang.gui("menu.incoming_requests.request.members", "count" to memberCount))
            .lore(lang.gui("menu.incoming_requests.request.received", "time" to timeAgo))

        // Add truce duration if applicable
        if (relation.type == RelationType.TRUCE && relation.expiresAt != null) {
            val durationDays = Duration.between(relation.createdAt, relation.expiresAt).toDays()
            item.lore(lang.gui("menu.incoming_requests.request.duration", "days" to durationDays))
        }

        item.lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.incoming_requests.request.respond"))

        return item
    }

    private fun openRequestActionMenu(relation: Relation) {
        // Create a small menu with accept/reject options
        val gui = ChestGui(3, MenuTitleBuilder.build(guild.guiTheme, 3, lang.guiTitle("menu.incoming_requests.title")))
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        val otherGuildId = relation.getOtherGuild(guild.id)
        val otherGuild = guildService.getGuild(otherGuildId)
        val guildName = otherGuild?.name ?: lang.raw("menu.incoming_requests.fallback.unknown_guild")

        // Accept button
        val acceptItem = ItemStack.of(Material.LIME_CONCRETE)
            .name(lang.gui("menu.incoming_requests.actions.accept.name"))
            .lore(lang.gui("menu.incoming_requests.actions.accept.description"))
            .lore(lang.gui("menu.incoming_requests.actions.guild", "guild" to guildName))

        val acceptGuiItem = GuiItem(acceptItem) {
            acceptRequest(relation, guildName)
        }
        pane.addItem(acceptGuiItem, 2, 1)

        // Reject button
        val rejectItem = ItemStack.of(Material.RED_CONCRETE)
            .name(lang.gui("menu.incoming_requests.actions.reject.name"))
            .lore(lang.gui("menu.incoming_requests.actions.reject.description"))
            .lore(lang.gui("menu.incoming_requests.actions.guild", "guild" to guildName))

        val rejectGuiItem = GuiItem(rejectItem) {
            rejectRequest(relation, guildName)
        }
        pane.addItem(rejectGuiItem, 6, 1)

        // Back button
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.incoming_requests.actions.back.name"))
            .lore(lang.gui("menu.incoming_requests.actions.back.description"))

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
                RelationType.ALLY -> lang.raw("menu.incoming_requests.type.alliance.accepted")
                RelationType.TRUCE -> lang.raw("menu.incoming_requests.type.truce.accepted")
                RelationType.NEUTRAL -> lang.raw("menu.incoming_requests.type.peace.accepted")
                else -> lang.raw("menu.incoming_requests.type.unknown.accepted")
            }

            player.sendMessage(lang.msg("menu.incoming_requests.feedback.accepted", "type" to typeName, "guild" to guildName))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f)

            // Notify the other guild
            notifyGuildMembers(relation.getOtherGuild(guild.id), lang.msg("menu.incoming_requests.notification.accepted", "guild" to guild.name, "type" to typeName))

            // Refresh the menu
            open()
        } else {
            player.sendMessage(lang.msg("menu.incoming_requests.feedback.accept_failed"))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            open()
        }
    }

    private fun rejectRequest(relation: Relation, guildName: String) {
        val success = relationService.rejectRequest(relation.id, guild.id, player.uniqueId)

        if (success) {
            val typeName = when (relation.type) {
                RelationType.ALLY -> lang.raw("menu.incoming_requests.type.alliance.rejected")
                RelationType.TRUCE -> lang.raw("menu.incoming_requests.type.truce.rejected")
                RelationType.NEUTRAL -> lang.raw("menu.incoming_requests.type.peace.rejected")
                else -> lang.raw("menu.incoming_requests.type.unknown.rejected")
            }

            player.sendMessage(lang.msg("menu.incoming_requests.feedback.rejected", "type" to typeName, "guild" to guildName))
            player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 0.8f)

            // Notify the other guild
            notifyGuildMembers(relation.getOtherGuild(guild.id), lang.msg("menu.incoming_requests.notification.rejected", "guild" to guild.name, "type" to typeName))

            // Refresh the menu
            open()
        } else {
            player.sendMessage(lang.msg("menu.incoming_requests.feedback.reject_failed"))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            open()
        }
    }

    private fun addNavigationButtons(pane: StaticPane) {
        val allRequests = relationService.getIncomingRequests(guild.id)
        val totalPages = maxOf(1, (allRequests.size + itemsPerPage - 1) / itemsPerPage)

        // Previous page button
        if (currentPage > 0) {
            val prevItem = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.incoming_requests.navigation.previous.name"))
                .lore(lang.gui("menu.incoming_requests.navigation.previous.description"))

            val prevGuiItem = GuiItem(prevItem) {
                currentPage--
                open()
            }
            pane.addItem(prevGuiItem, 0, 4)
        }

        // Page indicator
        val displayTotalPages = maxOf(totalPages, 1)
        val pageItem = ItemStack.of(Material.PAPER)
            .name(lang.gui("menu.incoming_requests.navigation.page", "page" to currentPage + 1, "pages" to displayTotalPages))
            .lore(lang.gui("menu.incoming_requests.navigation.total", "count" to allRequests.size))

        val pageGuiItem = GuiItem(pageItem) { }
        pane.addItem(pageGuiItem, 4, 4)

        // Next page button
        if (currentPage < totalPages - 1) {
            val nextItem = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.incoming_requests.navigation.next.name"))
                .lore(lang.gui("menu.incoming_requests.navigation.next.description"))

            val nextGuiItem = GuiItem(nextItem) {
                currentPage++
                open()
            }
            pane.addItem(nextGuiItem, 8, 4)
        }
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.incoming_requests.navigation.back.name"))
            .lore(lang.gui("menu.incoming_requests.navigation.back.description"))

        val guiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildRelationsMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun formatTimeAgo(instant: Instant): Component {
        val duration = Duration.between(instant, Instant.now())
        return when {
            duration.toMinutes() < 1 -> lang.gui("menu.incoming_requests.time.just_now")
            duration.toHours() < 1 -> lang.gui("menu.incoming_requests.time.minutes", "count" to duration.toMinutes())
            duration.toDays() < 1 -> lang.gui("menu.incoming_requests.time.hours", "count" to duration.toHours())
            duration.toDays() < 7 -> lang.gui("menu.incoming_requests.time.days", "count" to duration.toDays())
            else -> lang.gui("menu.incoming_requests.time.weeks", "count" to duration.toDays() / 7)
        }
    }

    private fun notifyGuildMembers(guildId: UUID, message: Component) {
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
