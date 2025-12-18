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

    private lateinit var enemiesPane: PaginatedPane
    private var currentPage = 0
    private val itemsPerPage = 28 // 4 rows x 7 columns

    override fun open() {
        val gui = ChestGui(6, "§cEnemy Guilds")
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
            val emptyItem = ItemStack(Material.WHITE_BANNER)
                .name("§7No Enemy Guilds")
                .lore("§7Your guild is not at war with anyone.")
                .lore("§7Use §6/guild enemy <guild>§7 to")
                .lore("§7declare war on another guild.")

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

        val guildName = otherGuild?.name ?: "Unknown Guild"
        val memberCount = otherGuild?.let { memberService.getMemberCount(it.id) } ?: 0

        // Calculate war duration
        val warDuration = Duration.between(relation.createdAt, Instant.now())
        val durationText = formatDuration(warDuration)

        // Try to use guild banner with red tint, fallback to red banner
        val item = if (otherGuild?.banner != null) {
            val deserialized = otherGuild.banner.deserializeToItemStack()
            deserialized ?: ItemStack(Material.RED_BANNER)
        } else {
            ItemStack(Material.RED_BANNER)
        }

        item.name("§c⚔ $guildName")
            .lore("§7Members: §f$memberCount")
            .lore("§7War Duration: §c$durationText")
            .lore("§7Level: §f${otherGuild?.level ?: 1}")
            .lore("§7Mode: ${if (otherGuild?.mode?.name == "PEACEFUL") "§a⚐ Peaceful" else "§c⚔ Hostile"}")
            .lore("§7")
            .lore("§eClick for actions")

        return item
    }

    private fun openEnemyActionsMenu(relation: Relation) {
        val otherGuildId = relation.getOtherGuild(guild.id)
        val otherGuild = guildService.getGuild(otherGuildId)
        val guildName = otherGuild?.name ?: "Unknown Guild"

        // Create actions menu
        val gui = ChestGui(3, "§c$guildName")
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // View info button
        val infoItem = ItemStack(Material.BOOK)
            .name("§eView Guild Info")
            .lore("§7View detailed information about")
            .lore("§f$guildName")

        val infoGuiItem = GuiItem(infoItem) {
            if (otherGuild != null) {
                menuNavigator.openMenu(menuFactory.createGuildInfoMenu(menuNavigator, player, otherGuild))
            }
        }
        pane.addItem(infoGuiItem, 1, 1)

        // Request truce button (requires MANAGE_RELATIONS permission)
        val hasManagePermission = memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RELATIONS)

        val truceItem = ItemStack(if (hasManagePermission) Material.WHITE_BANNER else Material.BARRIER)
            .name(if (hasManagePermission) "§eRequest Truce" else "§7Request Truce")
            .lore(if (hasManagePermission) "§7Request a temporary ceasefire" else "§7You need MANAGE_RELATIONS")
            .lore(if (hasManagePermission) "§7with §f$guildName" else "§7permission to request truces")
            .lore("§7")
            .lore(if (hasManagePermission) "§eUse: /guild truce $guildName [days]" else "§cPermission Required")

        val truceGuiItem = GuiItem(truceItem) {
            if (hasManagePermission) {
                player.closeInventory()
                player.sendMessage("§eTo request a truce, use:")
                player.sendMessage("§6/guild truce $guildName §7[days]")
                player.sendMessage("§7Default duration is 14 days if not specified.")
            } else {
                player.sendMessage("§cYou don't have permission to manage relations.")
                player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        }
        pane.addItem(truceGuiItem, 3, 1)

        // Request peace button (requires MANAGE_RELATIONS permission)
        val peaceItem = ItemStack(if (hasManagePermission) Material.PAPER else Material.BARRIER)
            .name(if (hasManagePermission) "§fRequest Peace" else "§7Request Peace")
            .lore(if (hasManagePermission) "§7Request to end hostilities" else "§7You need MANAGE_RELATIONS")
            .lore(if (hasManagePermission) "§7permanently with §f$guildName" else "§7permission to request peace")
            .lore("§7")
            .lore(if (hasManagePermission) "§fUse: /guild neutral $guildName" else "§cPermission Required")

        val peaceGuiItem = GuiItem(peaceItem) {
            if (hasManagePermission) {
                player.closeInventory()
                player.sendMessage("§fTo request peace, use:")
                player.sendMessage("§6/guild neutral $guildName")
                player.sendMessage("§7This will end hostilities permanently if accepted.")
            } else {
                player.sendMessage("§cYou don't have permission to manage relations.")
                player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        }
        pane.addItem(peaceGuiItem, 5, 1)

        // War details button
        val warDetailsItem = ItemStack(Material.IRON_SWORD)
            .name("§cWar Details")
            .lore("§7View war statistics and history")
            .lore("§7")
            .lore("§7War started: §f${formatDuration(Duration.between(relation.createdAt, Instant.now()))} ago")

        val warDetailsGuiItem = GuiItem(warDetailsItem) {
            player.sendMessage("§c=== War with $guildName ===")
            player.sendMessage("§7Started: ${formatDuration(Duration.between(relation.createdAt, Instant.now()))} ago")
            player.sendMessage("§7")
            player.sendMessage("§7War details and statistics coming soon!")
        }
        pane.addItem(warDetailsGuiItem, 7, 1)

        // Back button
        val backItem = ItemStack(Material.ARROW)
            .name("§eBack")
            .lore("§7Return to enemies list")

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
            .lore("§7Total enemies: §c${allEnemies.size}")

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

    private fun formatDuration(duration: Duration): String {
        val days = duration.toDays()
        return when {
            days < 1 -> "Less than 1 day"
            days < 7 -> "$days day${if (days == 1L) "" else "s"}"
            days < 30 -> "${days / 7} week${if (days / 7 == 1L) "" else "s"}"
            days < 365 -> "${days / 30} month${if (days / 30 == 1L) "" else "s"}"
            else -> "${days / 365} year${if (days / 365 == 1L) "" else "s"}"
        }
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
