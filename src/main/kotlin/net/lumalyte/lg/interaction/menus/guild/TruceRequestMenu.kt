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
import java.time.Duration
import java.util.*

class TruceRequestMenu(
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

        val gui = ChestGui(6, "§eRequest Truce")
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
        // Get all enemy guilds that can have truces requested
        val enemies = relationService.getGuildRelationsByType(guild.id, RelationType.ENEMY)
            .filter { it.isActive() }
            .mapNotNull { relation ->
                val otherGuildId = relation.getOtherGuild(guild.id)
                guildService.getGuild(otherGuildId)
            }
            .sortedBy { it.name }

        // Calculate pagination
        val totalPages = (enemies.size + itemsPerPage - 1) / itemsPerPage
        if (currentPage >= totalPages && totalPages > 0) {
            currentPage = totalPages - 1
        }

        // Get guilds for current page
        val startIndex = currentPage * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, enemies.size)
        val pageGuilds = enemies.subList(startIndex, endIndex)

        // Clear existing panes
        guildsPane.clear()

        val newPage = StaticPane(0, 0, 7, 4)

        if (pageGuilds.isEmpty()) {
            // No enemy guilds
            val emptyItem = ItemStack(Material.WHITE_BANNER)
                .name("§7No Enemy Guilds")
                .lore("§7You are not at war with any guilds.")
                .lore("§7Truces can only be requested with enemies.")

            val guiItem = GuiItem(emptyItem) { }
            newPage.addItem(guiItem, 3, 1)
        } else {
            // Add enemy guild items to the page
            for ((index, targetGuild) in pageGuilds.withIndex()) {
                val x = index % 7
                val y = index / 7
                val guildItem = createGuildItem(targetGuild)
                val guiItem = GuiItem(guildItem) {
                    openDurationSelection(targetGuild)
                }
                newPage.addItem(guiItem, x, y)
            }
        }

        guildsPane.addPage(newPage)
        guildsPane.page = 0
    }

    private fun createGuildItem(targetGuild: Guild): ItemStack {
        val memberCount = memberService.getMemberCount(targetGuild.id)

        // Try to use guild banner, fallback to white banner
        val item = if (targetGuild.banner != null) {
            val deserialized = targetGuild.banner.deserializeToItemStack()
            deserialized ?: ItemStack(Material.WHITE_BANNER)
        } else {
            ItemStack(Material.WHITE_BANNER)
        }

        item.name("§c${targetGuild.name}")
            .lore("§7Members: §f$memberCount")
            .lore("§7Level: §f${targetGuild.level}")
            .lore("§7Status: §cEnemy")
            .lore("§7")
            .lore("§eClick to select duration")

        return item
    }

    private fun openDurationSelection(targetGuild: Guild) {
        val gui = ChestGui(3, "§eSelect Truce Duration")
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // 7 days option
        val sevenDaysItem = ItemStack(Material.CLOCK)
            .name("§e7 Days")
            .lore("§7Request a 7-day truce")
            .lore("§7with §f${targetGuild.name}")

        val sevenDaysGuiItem = GuiItem(sevenDaysItem) {
            requestTruce(targetGuild, 7)
        }
        pane.addItem(sevenDaysGuiItem, 1, 1)

        // 14 days option (default)
        val fourteenDaysItem = ItemStack(Material.CLOCK)
            .name("§e14 Days §7(Recommended)")
            .lore("§7Request a 14-day truce")
            .lore("§7with §f${targetGuild.name}")

        val fourteenDaysGuiItem = GuiItem(fourteenDaysItem) {
            requestTruce(targetGuild, 14)
        }
        pane.addItem(fourteenDaysGuiItem, 3, 1)

        // 30 days option
        val thirtyDaysItem = ItemStack(Material.CLOCK)
            .name("§e30 Days")
            .lore("§7Request a 30-day truce")
            .lore("§7with §f${targetGuild.name}")

        val thirtyDaysGuiItem = GuiItem(thirtyDaysItem) {
            requestTruce(targetGuild, 30)
        }
        pane.addItem(thirtyDaysGuiItem, 5, 1)

        // Custom duration option
        val customItem = ItemStack(Material.WRITABLE_BOOK)
            .name("§eCustom Duration")
            .lore("§7Enter a custom duration")
            .lore("§7in chat (1-90 days)")

        val customGuiItem = GuiItem(customItem) {
            player.closeInventory()
            player.sendMessage("§eTo request a custom truce duration, use:")
            player.sendMessage("§6/guild truce ${targetGuild.name} <days>")
            player.sendMessage("§7Example: §6/guild truce ${targetGuild.name} 21")
        }
        pane.addItem(customGuiItem, 7, 1)

        // Back button
        val backItem = ItemStack(Material.ARROW)
            .name("§eBack")
            .lore("§7Return to enemy selection")

        val backGuiItem = GuiItem(backItem) {
            open()
        }
        pane.addItem(backGuiItem, 4, 2)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun requestTruce(targetGuild: Guild, durationDays: Int) {
        val duration = Duration.ofDays(durationDays.toLong())
        val relation = relationService.requestTruce(guild.id, targetGuild.id, player.uniqueId, duration)

        if (relation != null) {
            player.closeInventory()
            player.sendMessage("§e✓ Truce request sent to ${targetGuild.name} for $durationDays days!")
            player.sendMessage("§7They must accept your request for the truce to become active.")
            player.playSound(player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f)

            // Notify target guild members
            notifyGuildMembers(targetGuild.id, "§e${guild.name} §7has requested a §e$durationDays-day truce§7 with your guild! Use §6/guild menu §7→ Relations to respond.")
        } else {
            player.sendMessage("§c✗ Failed to send truce request.")
            player.sendMessage("§7There may already be a pending request.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
        }
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
            .lore("§7Enemy guilds: §c${allEnemies.size}")

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
