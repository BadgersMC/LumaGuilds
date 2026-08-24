package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Relation
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
import java.time.Duration
import java.time.Instant
import java.util.*

class AlliesListMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val relationService: RelationService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val lang: LangService by inject()

    private lateinit var alliesPane: PaginatedPane
    private var currentPage = 0
    private val itemsPerPage = 28 // 4 rows x 7 columns

    override fun open() {
        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.guiTitle("menu.allies_list.title")))
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // Initialize allies display pane
        alliesPane = PaginatedPane(1, 0, 7, 4)
        updateAlliesDisplay()

        // Add navigation buttons
        addNavigationButtons(pane)

        // Add back button
        addBackButton(pane, 4, 5)

        gui.addPane(alliesPane)
        gui.addPane(pane)
        gui.show(player)
    }

    private fun updateAlliesDisplay() {
        val allies = relationService.getGuildRelationsByType(guild.id, RelationType.ALLY)
            .filter { it.isActive() }
            .sortedByDescending { it.createdAt }

        // Calculate pagination
        val totalPages = (allies.size + itemsPerPage - 1) / itemsPerPage
        if (currentPage >= totalPages && totalPages > 0) {
            currentPage = totalPages - 1
        }

        // Get allies for current page
        val startIndex = currentPage * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, allies.size)
        val pageAllies = allies.toList().subList(startIndex, endIndex)

        // Clear existing panes
        alliesPane.clear()

        val newPage = StaticPane(0, 0, 7, 4)

        if (pageAllies.isEmpty()) {
            // No allies - show empty message
            val emptyItem = ItemStack.of(Material.BARRIER)
                .name(lang.gui("menu.allies_list.empty.name"))
                .lore(lang.gui("menu.allies_list.empty.description"))
                .lore(lang.gui("menu.allies_list.empty.command"))
                .lore(lang.gui("menu.allies_list.empty.hint"))

            val guiItem = GuiItem(emptyItem) { }
            newPage.addItem(guiItem, 3, 1)
        } else {
            // Add ally items to the page
            for ((index, relation) in pageAllies.withIndex()) {
                val x = index % 7
                val y = index / 7
                val allyItem = createAllyItem(relation)
                val guiItem = GuiItem(allyItem) {
                    openAllyActionsMenu(relation)
                }
                newPage.addItem(guiItem, x, y)
            }
        }

        alliesPane.addPage(newPage)
        alliesPane.page = 0
    }

    private fun createAllyItem(relation: Relation): ItemStack {
        // Get the other guild
        val otherGuildId = relation.getOtherGuild(guild.id)
        val otherGuild = guildService.getGuild(otherGuildId)

        val guildName = otherGuild?.name ?: lang.raw("menu.allies_list.fallback.unknown_guild")
        val memberCount = otherGuild?.let { memberService.getMemberCount(it.id) } ?: 0

        // Calculate alliance duration
        val allianceDuration = Duration.between(relation.createdAt, Instant.now())
        val durationText = formatDuration(allianceDuration)

        // Try to use guild banner, fallback to default
        val item = if (otherGuild?.banner != null) {
            val deserialized = otherGuild.banner.deserializeToItemStack()
            deserialized ?: ItemStack.of(Material.GREEN_BANNER)
        } else {
            ItemStack.of(Material.GREEN_BANNER)
        }

        val mode = if (otherGuild?.mode?.name == "PEACEFUL") {
            lang.gui("menu.allies_list.guild.mode.peaceful")
        } else {
            lang.gui("menu.allies_list.guild.mode.hostile")
        }
        item.name(lang.gui("menu.allies_list.guild.name", "guild" to guildName))
            .lore(lang.gui("menu.allies_list.guild.members", "count" to memberCount))
            .lore(lang.gui("menu.allies_list.guild.duration", "duration" to durationText))
            .lore(lang.gui("menu.allies_list.guild.level", "level" to (otherGuild?.level ?: 1)))
            .lore(lang.gui("menu.allies_list.guild.mode.line", "mode" to mode))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.allies_list.guild.actions"))

        return item
    }

    private fun openAllyActionsMenu(relation: Relation) {
        val otherGuildId = relation.getOtherGuild(guild.id)
        val otherGuild = guildService.getGuild(otherGuildId)
        val guildName = otherGuild?.name ?: lang.raw("menu.allies_list.fallback.unknown_guild")

        // Create actions menu
        val gui = ChestGui(3, MenuTitleBuilder.build(guild.guiTheme, 3, lang.guiTitle("menu.allies_list.actions_title", "guild" to guildName)))
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // View info button
        val infoItem = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.allies_list.actions.info.name"))
            .lore(lang.gui("menu.allies_list.actions.info.description"))
            .lore(lang.gui("menu.allies_list.actions.info.guild", "guild" to guildName))

        val infoGuiItem = GuiItem(infoItem) {
            if (otherGuild != null) {
                menuNavigator.openMenu(menuFactory.createGuildInfoMenu(menuNavigator, player, otherGuild))
            }
        }
        pane.addItem(infoGuiItem, 2, 1)

        // Break alliance button (requires MANAGE_RELATIONS permission)
        val hasPermission = memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RELATIONS)

        val breakItem = ItemStack.of(if (hasPermission) Material.RED_CONCRETE else Material.BARRIER)
            .name(if (hasPermission) lang.gui("menu.allies_list.actions.break_alliance.name") else lang.gui("menu.allies_list.actions.break_alliance.disabled"))
            .lore(if (hasPermission) lang.gui("menu.allies_list.actions.break_alliance.description") else lang.gui("menu.allies_list.permission.manage_relations"))
            .lore(if (hasPermission) lang.gui("menu.allies_list.actions.break_alliance.guild", "guild" to guildName) else lang.gui("menu.allies_list.actions.break_alliance.permission"))
            .lore(lang.gui("menu.common.blank"))
            .lore(if (hasPermission) lang.gui("menu.allies_list.actions.break_alliance.warning") else lang.gui("menu.allies_list.permission.required"))

        val breakGuiItem = GuiItem(breakItem) {
            if (hasPermission) {
                openBreakConfirmMenu(relation, guildName)
            } else {
                player.sendMessage(lang.msg("menu.allies_list.feedback.no_permission"))
                player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        }
        pane.addItem(breakGuiItem, 6, 1)

        // Back button
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.allies_list.actions.back.name"))
            .lore(lang.gui("menu.allies_list.actions.back.description"))

        val backGuiItem = GuiItem(backItem) {
            open()
        }
        pane.addItem(backGuiItem, 4, 2)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun openBreakConfirmMenu(relation: Relation, guildName: String) {
        // Create confirmation menu
        val gui = ChestGui(3, MenuTitleBuilder.build(guild.guiTheme, 3, lang.guiTitle("menu.allies_list.confirm.title")))
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // Confirm button
        val confirmItem = ItemStack.of(Material.RED_CONCRETE)
            .name(lang.gui("menu.allies_list.confirm.name"))
            .lore(lang.gui("menu.allies_list.confirm.description"))
            .lore(lang.gui("menu.allies_list.confirm.guild", "guild" to guildName))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.allies_list.confirm.warning"))

        val confirmGuiItem = GuiItem(confirmItem) {
            breakAlliance(relation, guildName)
        }
        pane.addItem(confirmGuiItem, 3, 1)

        // Cancel button
        val cancelItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.allies_list.confirm.cancel.name"))
            .lore(lang.gui("menu.allies_list.confirm.cancel.description"))

        val cancelGuiItem = GuiItem(cancelItem) {
            openAllyActionsMenu(relation)
        }
        pane.addItem(cancelGuiItem, 5, 1)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun breakAlliance(relation: Relation, guildName: String) {
        // Use repository to remove relation (unilateral break)
        val relationRepository: net.lumalyte.lg.application.persistence.RelationRepository by inject()
        val success = relationRepository.remove(relation.id)

        if (success) {
            player.sendMessage(lang.msg("menu.allies_list.feedback.broken", "guild" to guildName))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f)

            // Notify the other guild
            notifyGuildMembers(relation.getOtherGuild(guild.id), lang.msg("menu.allies_list.notification.broken", "guild" to guild.name))

            // Refresh the menu
            open()
        } else {
            player.sendMessage(lang.msg("menu.allies_list.feedback.break_failed"))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            open()
        }
    }

    private fun addNavigationButtons(pane: StaticPane) {
        val allAllies = relationService.getGuildRelationsByType(guild.id, RelationType.ALLY)
            .filter { it.isActive() }
        val totalPages = (allAllies.size + itemsPerPage - 1) / itemsPerPage

        // Previous page button
        if (currentPage > 0) {
            val prevItem = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.allies_list.navigation.previous.name"))
                .lore(lang.gui("menu.allies_list.navigation.previous.description"))

            val prevGuiItem = GuiItem(prevItem) {
                currentPage--
                open()
            }
            pane.addItem(prevGuiItem, 0, 4)
        }

        // Page indicator
        val pageItem = ItemStack.of(Material.PAPER)
            .name(lang.gui("menu.allies_list.navigation.page", "page" to currentPage + 1, "pages" to if (totalPages > 0) totalPages else 1))
            .lore(lang.gui("menu.allies_list.navigation.total", "count" to allAllies.size))

        val pageGuiItem = GuiItem(pageItem) { }
        pane.addItem(pageGuiItem, 4, 4)

        // Next page button
        if (currentPage < totalPages - 1) {
            val nextItem = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.allies_list.navigation.next.name"))
                .lore(lang.gui("menu.allies_list.navigation.next.description"))

            val nextGuiItem = GuiItem(nextItem) {
                currentPage++
                open()
            }
            pane.addItem(nextGuiItem, 8, 4)
        }
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.allies_list.navigation.back.name"))
            .lore(lang.gui("menu.allies_list.navigation.back.description"))

        val guiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildRelationsMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun formatDuration(duration: Duration): Component {
        val days = duration.toDays()
        return when {
            days < 1 -> lang.gui("menu.allies_list.duration.less_than_day")
            days < 7 && days == 1L -> lang.gui("menu.allies_list.duration.day", "count" to days)
            days < 7 -> lang.gui("menu.allies_list.duration.days", "count" to days)
            days < 30 && days / 7 == 1L -> lang.gui("menu.allies_list.duration.week", "count" to days / 7)
            days < 30 -> lang.gui("menu.allies_list.duration.weeks", "count" to days / 7)
            days < 365 && days / 30 == 1L -> lang.gui("menu.allies_list.duration.month", "count" to days / 30)
            days < 365 -> lang.gui("menu.allies_list.duration.months", "count" to days / 30)
            days / 365 == 1L -> lang.gui("menu.allies_list.duration.year", "count" to days / 365)
            else -> lang.gui("menu.allies_list.duration.years", "count" to days / 365)
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
