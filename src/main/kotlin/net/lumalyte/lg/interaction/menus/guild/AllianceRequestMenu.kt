package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
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
    private val lang: LangService by inject()

    private lateinit var guildsPane: PaginatedPane
    private var currentPage = 0
    private val itemsPerPage = 28 // 4 rows x 7 columns

    override fun open() {
        // Check permission
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RELATIONS)) {
            player.sendMessage(lang.msg("menu.alliance_request.permission_denied"))
            return
        }

        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.legacy("menu.alliance_request.title")))
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
            val emptyItem = ItemStack.of(Material.BARRIER)
                .name(lang.legacy("menu.alliance_request.empty.name"))
                .lore(lang.legacy("menu.alliance_request.empty.description"))
                .lore(lang.legacy("menu.alliance_request.empty.detail"))

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
            deserialized ?: ItemStack.of(Material.GREEN_BANNER)
        } else {
            ItemStack.of(Material.GREEN_BANNER)
        }

        item.name(lang.legacy("menu.alliance_request.guild.name", "guild" to targetGuild.name))
            .lore(lang.legacy("menu.alliance_request.guild.members", "count" to memberCount))
            .lore(lang.legacy("menu.alliance_request.guild.level", "level" to targetGuild.level))
            .lore(
                if (targetGuild.mode.name == "PEACEFUL") {
                    lang.legacy("menu.alliance_request.guild.mode.peaceful")
                } else {
                    lang.legacy("menu.alliance_request.guild.mode.hostile")
                }
            )
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.alliance_request.guild.action"))

        return item
    }

    private fun requestAlliance(targetGuild: Guild) {
        val relation = relationService.requestAlliance(guild.id, targetGuild.id, player.uniqueId)

        if (relation != null) {
            player.closeInventory()
            player.sendMessage(lang.msg("menu.alliance_request.feedback.sent", "guild" to targetGuild.name))
            player.sendMessage(lang.msg("menu.alliance_request.feedback.pending"))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f)

            // Notify target guild members
            notifyGuildMembers(targetGuild.id, lang.msg("menu.alliance_request.notification.received", "guild" to guild.name))
        } else {
            player.sendMessage(lang.msg("menu.alliance_request.feedback.failed"))
            player.sendMessage(lang.msg("menu.alliance_request.feedback.failure_hint"))
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
            val prevItem = ItemStack.of(Material.ARROW)
                .name(lang.legacy("menu.alliance_request.navigation.previous.name"))
                .lore(lang.legacy("menu.alliance_request.navigation.previous.description"))

            val prevGuiItem = GuiItem(prevItem) {
                currentPage--
                open()
            }
            pane.addItem(prevGuiItem, 0, 4)
        }

        // Page indicator
        val pageItem = ItemStack.of(Material.PAPER)
            .name(lang.legacy("menu.alliance_request.navigation.page", "page" to currentPage + 1, "pages" to if (totalPages > 0) totalPages else 1))
            .lore(lang.legacy("menu.alliance_request.navigation.total", "count" to allGuilds.size))

        val pageGuiItem = GuiItem(pageItem) { }
        pane.addItem(pageGuiItem, 4, 4)

        // Next page button
        if (currentPage < totalPages - 1) {
            val nextItem = ItemStack.of(Material.ARROW)
                .name(lang.legacy("menu.alliance_request.navigation.next.name"))
                .lore(lang.legacy("menu.alliance_request.navigation.next.description"))

            val nextGuiItem = GuiItem(nextItem) {
                currentPage++
                open()
            }
            pane.addItem(nextGuiItem, 8, 4)
        }
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.legacy("menu.alliance_request.navigation.back.name"))
            .lore(lang.legacy("menu.alliance_request.navigation.back.description"))

        val guiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildRelationsMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
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
