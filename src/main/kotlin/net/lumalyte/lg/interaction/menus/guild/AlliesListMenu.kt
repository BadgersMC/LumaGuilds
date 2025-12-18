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

class AlliesListMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val relationService: RelationService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    private lateinit var alliesPane: PaginatedPane
    private var currentPage = 0
    private val itemsPerPage = 28 // 4 rows x 7 columns

    override fun open() {
        val gui = ChestGui(6, "§aAllied Guilds")
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
            val emptyItem = ItemStack(Material.BARRIER)
                .name("§7No Allied Guilds")
                .lore("§7You currently have no alliances.")
                .lore("§7Use §6/guild ally <guild>§7 or the")
                .lore("§7diplomatic actions to form alliances.")

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

        val guildName = otherGuild?.name ?: "Unknown Guild"
        val memberCount = otherGuild?.let { memberService.getMemberCount(it.id) } ?: 0

        // Calculate alliance duration
        val allianceDuration = Duration.between(relation.createdAt, Instant.now())
        val durationText = formatDuration(allianceDuration)

        // Try to use guild banner, fallback to default
        val item = if (otherGuild?.banner != null) {
            val deserialized = otherGuild.banner.deserializeToItemStack()
            deserialized ?: ItemStack(Material.GREEN_BANNER)
        } else {
            ItemStack(Material.GREEN_BANNER)
        }

        item.name("§a✦ $guildName")
            .lore("§7Members: §f$memberCount")
            .lore("§7Alliance Duration: §a$durationText")
            .lore("§7Level: §f${otherGuild?.level ?: 1}")
            .lore("§7Mode: ${if (otherGuild?.mode?.name == "PEACEFUL") "§a⚐ Peaceful" else "§c⚔ Hostile"}")
            .lore("§7")
            .lore("§eClick for actions")

        return item
    }

    private fun openAllyActionsMenu(relation: Relation) {
        val otherGuildId = relation.getOtherGuild(guild.id)
        val otherGuild = guildService.getGuild(otherGuildId)
        val guildName = otherGuild?.name ?: "Unknown Guild"

        // Create actions menu
        val gui = ChestGui(3, "§a$guildName")
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
        pane.addItem(infoGuiItem, 2, 1)

        // Break alliance button (requires MANAGE_RELATIONS permission)
        val hasPermission = memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RELATIONS)

        val breakItem = ItemStack(if (hasPermission) Material.RED_CONCRETE else Material.BARRIER)
            .name(if (hasPermission) "§cBreak Alliance" else "§7Break Alliance")
            .lore(if (hasPermission) "§7End your alliance with" else "§7You need MANAGE_RELATIONS")
            .lore(if (hasPermission) "§f$guildName" else "§7permission to break alliances")
            .lore("§7")
            .lore(if (hasPermission) "§cThis action cannot be undone." else "§cPermission Required")

        val breakGuiItem = GuiItem(breakItem) {
            if (hasPermission) {
                openBreakConfirmMenu(relation, guildName)
            } else {
                player.sendMessage("§cYou don't have permission to manage relations.")
                player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        }
        pane.addItem(breakGuiItem, 6, 1)

        // Back button
        val backItem = ItemStack(Material.ARROW)
            .name("§eBack")
            .lore("§7Return to allies list")

        val backGuiItem = GuiItem(backItem) {
            open()
        }
        pane.addItem(backGuiItem, 4, 2)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun openBreakConfirmMenu(relation: Relation, guildName: String) {
        // Create confirmation menu
        val gui = ChestGui(3, "§cBreak Alliance?")
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // Confirm button
        val confirmItem = ItemStack(Material.RED_CONCRETE)
            .name("§c✗ Confirm Break Alliance")
            .lore("§7Break your alliance with")
            .lore("§f$guildName")
            .lore("§7")
            .lore("§cThis cannot be undone!")

        val confirmGuiItem = GuiItem(confirmItem) {
            breakAlliance(relation, guildName)
        }
        pane.addItem(confirmGuiItem, 3, 1)

        // Cancel button
        val cancelItem = ItemStack(Material.ARROW)
            .name("§eCancel")
            .lore("§7Return to ally actions")

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
            player.sendMessage("§c✗ You broke the alliance with $guildName.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f)

            // Notify the other guild
            notifyGuildMembers(relation.getOtherGuild(guild.id), "§c${guild.name} §7has broken the alliance with your guild.")

            // Refresh the menu
            open()
        } else {
            player.sendMessage("§c✗ Failed to break alliance.")
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
            .lore("§7Total allies: §a${allAllies.size}")

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
