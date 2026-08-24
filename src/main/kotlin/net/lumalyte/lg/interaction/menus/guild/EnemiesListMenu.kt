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

class EnemiesListMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val relationService: RelationService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val lang: LangService by inject()

    private lateinit var enemiesPane: PaginatedPane
    private var currentPage = 0
    private val itemsPerPage = 28 // 4 rows x 7 columns

    override fun open() {
        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.guiTitle("menu.enemies_list.title")))
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // Initialize enemies display pane
        enemiesPane = PaginatedPane(1, 0, 7, 4)
        updateEnemiesDisplay()

        // Add navigation buttons
        addNavigationButtons(pane)

        // Add back button
        addBackButton(pane, 4, 5)

        gui.addPane(enemiesPane)
        gui.addPane(pane)
        gui.show(player)
    }

    private fun updateEnemiesDisplay() {
        val enemies = relationService.getGuildRelationsByType(guild.id, RelationType.ENEMY)
            .filter { it.isActive() }
            .sortedByDescending { it.createdAt }

        // Calculate pagination
        val totalPages = (enemies.size + itemsPerPage - 1) / itemsPerPage
        if (currentPage >= totalPages && totalPages > 0) {
            currentPage = totalPages - 1
        }

        // Get enemies for current page
        val startIndex = currentPage * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, enemies.size)
        val pageEnemies = enemies.toList().subList(startIndex, endIndex)

        // Clear existing panes
        enemiesPane.clear()

        val newPage = StaticPane(0, 0, 7, 4)

        if (pageEnemies.isEmpty()) {
            // No enemies - show empty message
            val emptyItem = ItemStack.of(Material.WHITE_BANNER)
                .name(lang.gui("menu.enemies_list.empty.name"))
                .lore(lang.gui("menu.enemies_list.empty.description"))
                .lore(lang.gui("menu.enemies_list.empty.command"))
                .lore(lang.gui("menu.enemies_list.empty.hint"))

            val guiItem = GuiItem(emptyItem) { }
            newPage.addItem(guiItem, 3, 1)
        } else {
            // Add enemy items to the page
            for ((index, relation) in pageEnemies.withIndex()) {
                val x = index % 7
                val y = index / 7
                val enemyItem = createEnemyItem(relation)
                val guiItem = GuiItem(enemyItem) {
                    openEnemyActionsMenu(relation)
                }
                newPage.addItem(guiItem, x, y)
            }
        }

        enemiesPane.addPage(newPage)
        enemiesPane.page = 0
    }

    private fun createEnemyItem(relation: Relation): ItemStack {
        // Get the other guild
        val otherGuildId = relation.getOtherGuild(guild.id)
        val otherGuild = guildService.getGuild(otherGuildId)

        val guildName = otherGuild?.name ?: lang.raw("menu.enemies_list.fallback.unknown_guild")
        val memberCount = otherGuild?.let { memberService.getMemberCount(it.id) } ?: 0

        // Calculate war duration
        val warDuration = Duration.between(relation.createdAt, Instant.now())
        val durationText = formatDuration(warDuration)

        // Try to use guild banner with red tint, fallback to red banner
        val item = if (otherGuild?.banner != null) {
            val deserialized = otherGuild.banner.deserializeToItemStack()
            deserialized ?: ItemStack.of(Material.RED_BANNER)
        } else {
            ItemStack.of(Material.RED_BANNER)
        }

        val mode = if (otherGuild?.mode?.name == "PEACEFUL") {
            lang.gui("menu.enemies_list.guild.mode.peaceful")
        } else {
            lang.gui("menu.enemies_list.guild.mode.hostile")
        }
        item.name(lang.gui("menu.enemies_list.guild.name", "guild" to guildName))
            .lore(lang.gui("menu.enemies_list.guild.members", "count" to memberCount))
            .lore(lang.gui("menu.enemies_list.guild.duration", "duration" to durationText))
            .lore(lang.gui("menu.enemies_list.guild.level", "level" to (otherGuild?.level ?: 1)))
            .lore(lang.gui("menu.enemies_list.guild.mode.line", "mode" to mode))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.enemies_list.guild.actions"))

        return item
    }

    private fun openEnemyActionsMenu(relation: Relation) {
        val otherGuildId = relation.getOtherGuild(guild.id)
        val otherGuild = guildService.getGuild(otherGuildId)
        val guildName = otherGuild?.name ?: lang.raw("menu.enemies_list.fallback.unknown_guild")

        // Create actions menu
        val gui = ChestGui(3, MenuTitleBuilder.build(guild.guiTheme, 3, lang.guiTitle("menu.enemies_list.title")))
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // View info button
        val infoItem = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.enemies_list.actions.info.name"))
            .lore(lang.gui("menu.enemies_list.actions.info.description"))
            .lore(lang.gui("menu.enemies_list.actions.info.guild", "guild" to guildName))

        val infoGuiItem = GuiItem(infoItem) {
            if (otherGuild != null) {
                menuNavigator.openMenu(menuFactory.createGuildInfoMenu(menuNavigator, player, otherGuild))
            }
        }
        pane.addItem(infoGuiItem, 1, 1)

        // Request truce button (requires MANAGE_RELATIONS permission)
        val hasManagePermission = memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RELATIONS)

        val truceItem = ItemStack.of(if (hasManagePermission) Material.WHITE_BANNER else Material.BARRIER)
            .name(if (hasManagePermission) lang.gui("menu.enemies_list.actions.truce.name") else lang.gui("menu.enemies_list.actions.truce.disabled"))
            .lore(if (hasManagePermission) lang.gui("menu.enemies_list.actions.truce.description") else lang.gui("menu.enemies_list.permission.required_relation"))
            .lore(if (hasManagePermission) lang.gui("menu.enemies_list.actions.truce.guild", "guild" to guildName) else lang.gui("menu.enemies_list.actions.truce.permission"))
            .lore(lang.gui("menu.common.blank"))
            .lore(if (hasManagePermission) lang.gui("menu.enemies_list.actions.truce.command", "guild" to guildName) else lang.gui("menu.enemies_list.permission.required"))

        val truceGuiItem = GuiItem(truceItem) {
            if (hasManagePermission) {
                player.closeInventory()
                player.sendMessage(lang.msg("menu.enemies_list.feedback.truce_intro"))
                player.sendMessage(lang.msg("menu.enemies_list.feedback.truce_command", "guild" to guildName))
                player.sendMessage(lang.msg("menu.enemies_list.feedback.truce_default"))
            } else {
                player.sendMessage(lang.msg("menu.enemies_list.feedback.no_permission"))
                player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        }
        pane.addItem(truceGuiItem, 3, 1)

        // Request peace button (requires MANAGE_RELATIONS permission)
        val peaceItem = ItemStack.of(if (hasManagePermission) Material.PAPER else Material.BARRIER)
            .name(if (hasManagePermission) lang.gui("menu.enemies_list.actions.peace.name") else lang.gui("menu.enemies_list.actions.peace.disabled"))
            .lore(if (hasManagePermission) lang.gui("menu.enemies_list.actions.peace.description") else lang.gui("menu.enemies_list.permission.required_relation"))
            .lore(if (hasManagePermission) lang.gui("menu.enemies_list.actions.peace.guild", "guild" to guildName) else lang.gui("menu.enemies_list.actions.peace.permission"))
            .lore(lang.gui("menu.common.blank"))
            .lore(if (hasManagePermission) lang.gui("menu.enemies_list.actions.peace.command", "guild" to guildName) else lang.gui("menu.enemies_list.permission.required"))

        val peaceGuiItem = GuiItem(peaceItem) {
            if (hasManagePermission) {
                player.closeInventory()
                player.sendMessage(lang.msg("menu.enemies_list.feedback.peace_intro"))
                player.sendMessage(lang.msg("menu.enemies_list.feedback.peace_command", "guild" to guildName))
                player.sendMessage(lang.msg("menu.enemies_list.feedback.peace_result"))
            } else {
                player.sendMessage(lang.msg("menu.enemies_list.feedback.no_permission"))
                player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        }
        pane.addItem(peaceGuiItem, 5, 1)

        // War details button
        val warDetailsItem = ItemStack.of(Material.IRON_SWORD)
            .name(lang.gui("menu.enemies_list.actions.details.name"))
            .lore(lang.gui("menu.enemies_list.actions.details.description"))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.enemies_list.actions.details.started", "duration" to formatDuration(Duration.between(relation.createdAt, Instant.now()))))

        val warDetailsGuiItem = GuiItem(warDetailsItem) {
            player.sendMessage(lang.msg("menu.enemies_list.feedback.details_header", "guild" to guildName))
            player.sendMessage(lang.msg("menu.enemies_list.feedback.started", "duration" to formatDuration(Duration.between(relation.createdAt, Instant.now()))))
            player.sendMessage(lang.msg("menu.enemies_list.feedback.type", "type" to relation.type.name))
            if (relation.expiresAt != null) {
                player.sendMessage(lang.msg("menu.enemies_list.feedback.expires", "duration" to formatDuration(Duration.between(Instant.now(), relation.expiresAt))))
            }
        }
        pane.addItem(warDetailsGuiItem, 7, 1)

        // Back button
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.enemies_list.actions.back.name"))
            .lore(lang.gui("menu.enemies_list.actions.back.description"))

        val backGuiItem = GuiItem(backItem) {
            open()
        }
        pane.addItem(backGuiItem, 4, 2)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun addNavigationButtons(pane: StaticPane) {
        val allEnemies = relationService.getGuildRelationsByType(guild.id, RelationType.ENEMY)
            .filter { it.isActive() }
        val totalPages = (allEnemies.size + itemsPerPage - 1) / itemsPerPage

        // Previous page button
        if (currentPage > 0) {
            val prevItem = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.enemies_list.navigation.previous.name"))
                .lore(lang.gui("menu.enemies_list.navigation.previous.description"))

            val prevGuiItem = GuiItem(prevItem) {
                currentPage--
                open()
            }
            pane.addItem(prevGuiItem, 0, 4)
        }

        // Page indicator
        val pageItem = ItemStack.of(Material.PAPER)
            .name(lang.gui("menu.enemies_list.navigation.page", "page" to currentPage + 1, "pages" to if (totalPages > 0) totalPages else 1))
            .lore(lang.gui("menu.enemies_list.navigation.total", "count" to allEnemies.size))

        val pageGuiItem = GuiItem(pageItem) { }
        pane.addItem(pageGuiItem, 4, 4)

        // Next page button
        if (currentPage < totalPages - 1) {
            val nextItem = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.enemies_list.navigation.next.name"))
                .lore(lang.gui("menu.enemies_list.navigation.next.description"))

            val nextGuiItem = GuiItem(nextItem) {
                currentPage++
                open()
            }
            pane.addItem(nextGuiItem, 8, 4)
        }
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.enemies_list.navigation.back.name"))
            .lore(lang.gui("menu.enemies_list.navigation.back.description"))

        val guiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildRelationsMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun formatDuration(duration: Duration): Component {
        val days = duration.toDays()
        return when {
            days < 1 -> lang.gui("menu.enemies_list.duration.less_than_day")
            days < 7 && days == 1L -> lang.gui("menu.enemies_list.duration.day", "count" to days)
            days < 7 -> lang.gui("menu.enemies_list.duration.days", "count" to days)
            days < 30 && days / 7 == 1L -> lang.gui("menu.enemies_list.duration.week", "count" to days / 7)
            days < 30 -> lang.gui("menu.enemies_list.duration.weeks", "count" to days / 7)
            days < 365 && days / 30 == 1L -> lang.gui("menu.enemies_list.duration.month", "count" to days / 30)
            days < 365 -> lang.gui("menu.enemies_list.duration.months", "count" to days / 30)
            days / 365 == 1L -> lang.gui("menu.enemies_list.duration.year", "count" to days / 365)
            else -> lang.gui("menu.enemies_list.duration.years", "count" to days / 365)
        }
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
