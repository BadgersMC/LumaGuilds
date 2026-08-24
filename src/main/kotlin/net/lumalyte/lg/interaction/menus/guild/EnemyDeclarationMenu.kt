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

class EnemyDeclarationMenu(
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
        // Check DECLARE_WAR permission (specific permission for enemy declarations)
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.DECLARE_WAR)) {
            player.sendMessage(lang.msg("menu.enemy_declaration.permission_denied"))
            return
        }

        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.legacy("menu.enemy_declaration.title")))
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
        // Get all guilds that can be declared enemies (neutral only, not allies)
        val allGuilds = guildService.getAllGuilds()
            .filter { it.id != guild.id } // Exclude own guild
            .filter { targetGuild ->
                val relationType = relationService.getRelationType(guild.id, targetGuild.id)
                // Only show neutral guilds (not allies, not already enemies)
                relationType == RelationType.NEUTRAL
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
                .name(lang.legacy("menu.enemy_declaration.empty.name"))
                .lore(lang.legacy("menu.enemy_declaration.empty.description"))
                .lore(lang.legacy("menu.enemy_declaration.empty.detail"))

            val guiItem = GuiItem(emptyItem) { }
            newPage.addItem(guiItem, 3, 1)
        } else {
            // Add guild items to the page
            for ((index, targetGuild) in pageGuilds.withIndex()) {
                val x = index % 7
                val y = index / 7
                val guildItem = createGuildItem(targetGuild)
                val guiItem = GuiItem(guildItem) {
                    openConfirmation(targetGuild)
                }
                newPage.addItem(guiItem, x, y)
            }
        }

        guildsPane.addPage(newPage)
        guildsPane.page = 0
    }

    private fun createGuildItem(targetGuild: Guild): ItemStack {
        val memberCount = memberService.getMemberCount(targetGuild.id)

        // Try to use guild banner, fallback to red banner
        val item = if (targetGuild.banner != null) {
            val deserialized = targetGuild.banner.deserializeToItemStack()
            deserialized ?: ItemStack.of(Material.RED_BANNER)
        } else {
            ItemStack.of(Material.RED_BANNER)
        }

        item.name(lang.legacy("menu.enemy_declaration.guild.name", "guild" to targetGuild.name))
            .lore(lang.legacy("menu.enemy_declaration.guild.members", "count" to memberCount))
            .lore(lang.legacy("menu.enemy_declaration.guild.level", "level" to targetGuild.level))
            .lore(
                if (targetGuild.mode.name == "PEACEFUL") {
                    lang.legacy("menu.enemy_declaration.guild.mode.peaceful")
                } else {
                    lang.legacy("menu.enemy_declaration.guild.mode.hostile")
                }
            )
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.enemy_declaration.guild.action"))

        return item
    }

    private fun openConfirmation(targetGuild: Guild) {
        val gui = ChestGui(3, MenuTitleBuilder.build(guild.guiTheme, 3, lang.legacy("menu.enemy_declaration.title")))
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // Confirm button
        val confirmItem = ItemStack.of(Material.RED_CONCRETE)
            .name(lang.legacy("menu.enemy_declaration.confirm.name"))
            .lore(lang.legacy("menu.enemy_declaration.confirm.guild", "guild" to targetGuild.name))
            .lore(lang.legacy("menu.enemy_declaration.confirm.description"))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.enemy_declaration.confirm.warning"))

        val confirmGuiItem = GuiItem(confirmItem) {
            declareEnemy(targetGuild)
        }
        pane.addItem(confirmGuiItem, 3, 1)

        // Cancel button
        val cancelItem = ItemStack.of(Material.ARROW)
            .name(lang.legacy("menu.enemy_declaration.cancel.name"))
            .lore(lang.legacy("menu.enemy_declaration.cancel.description"))

        val cancelGuiItem = GuiItem(cancelItem) {
            open()
        }
        pane.addItem(cancelGuiItem, 5, 1)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun declareEnemy(targetGuild: Guild) {
        val relation = relationService.declareWar(guild.id, targetGuild.id, player.uniqueId)

        if (relation != null) {
            player.closeInventory()
            player.sendMessage(lang.msg("menu.enemy_declaration.feedback.success", "guild" to targetGuild.name))
            player.sendMessage(lang.msg("menu.enemy_declaration.feedback.relationship_changed"))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f)

            // Notify target guild members
            notifyGuildMembers(targetGuild.id, lang.msg("menu.enemy_declaration.notification.target", "guild" to guild.name))

            // Broadcast to all online players
            Bukkit.getOnlinePlayers().forEach { onlinePlayer ->
                if (onlinePlayer != player) {
                    onlinePlayer.sendMessage(lang.msg("menu.enemy_declaration.notification.broadcast", "guild" to guild.name, "target" to targetGuild.name))
                }
            }
        } else {
            player.sendMessage(lang.msg("menu.enemy_declaration.feedback.failed"))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            open()
        }
    }

    private fun addNavigationButtons(pane: StaticPane) {
        val allGuilds = guildService.getAllGuilds()
            .filter { it.id != guild.id }
            .filter { targetGuild ->
                relationService.getRelationType(guild.id, targetGuild.id) == RelationType.NEUTRAL
            }
        val totalPages = (allGuilds.size + itemsPerPage - 1) / itemsPerPage

        // Previous page button
        if (currentPage > 0) {
            val prevItem = ItemStack.of(Material.ARROW)
                .name(lang.legacy("menu.enemy_declaration.navigation.previous.name"))
                .lore(lang.legacy("menu.enemy_declaration.navigation.previous.description"))

            val prevGuiItem = GuiItem(prevItem) {
                currentPage--
                open()
            }
            pane.addItem(prevGuiItem, 0, 4)
        }

        // Page indicator
        val pageItem = ItemStack.of(Material.PAPER)
            .name(lang.legacy("menu.enemy_declaration.navigation.page", "page" to currentPage + 1, "pages" to if (totalPages > 0) totalPages else 1))
            .lore(lang.legacy("menu.enemy_declaration.navigation.total", "count" to allGuilds.size))

        val pageGuiItem = GuiItem(pageItem) { }
        pane.addItem(pageGuiItem, 4, 4)

        // Next page button
        if (currentPage < totalPages - 1) {
            val nextItem = ItemStack.of(Material.ARROW)
                .name(lang.legacy("menu.enemy_declaration.navigation.next.name"))
                .lore(lang.legacy("menu.enemy_declaration.navigation.next.description"))

            val nextGuiItem = GuiItem(nextItem) {
                currentPage++
                open()
            }
            pane.addItem(nextGuiItem, 8, 4)
        }
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.legacy("menu.enemy_declaration.navigation.back.name"))
            .lore(lang.legacy("menu.enemy_declaration.navigation.back.description"))

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
