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

    private lateinit var guildsPane: PaginatedPane
    private var currentPage = 0
    private val itemsPerPage = 28 // 4 rows x 7 columns

    override fun open() {
        // Check DECLARE_WAR permission (specific permission for enemy declarations)
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.DECLARE_WAR)) {
            player.sendMessage("§cYou don't have permission to declare enemy status.")
            return
        }

        val gui = ChestGui(6, "§cDeclare Enemy")
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
            val emptyItem = ItemStack(Material.BARRIER)
                .name("§7No Guilds Available")
                .lore("§7All guilds are either allies,")
                .lore("§7already enemies, or your own guild.")

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
            deserialized ?: ItemStack(Material.RED_BANNER)
        } else {
            ItemStack(Material.RED_BANNER)
        }

        item.name("§c${targetGuild.name}")
            .lore("§7Members: §f$memberCount")
            .lore("§7Level: §f${targetGuild.level}")
            .lore("§7Mode: ${if (targetGuild.mode.name == "PEACEFUL") "§a⚐ Peaceful" else "§c⚔ Hostile"}")
            .lore("§7")
            .lore("§cClick to declare enemy")

        return item
    }

    private fun openConfirmation(targetGuild: Guild) {
        val gui = ChestGui(3, "§cConfirm Enemy Declaration")
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // Confirm button
        val confirmItem = ItemStack(Material.RED_CONCRETE)
            .name("§c⚔ Confirm Enemy Declaration")
            .lore("§7Declare §f${targetGuild.name}")
            .lore("§7as an enemy guild.")
            .lore("§7")
            .lore("§cThis takes effect immediately!")

        val confirmGuiItem = GuiItem(confirmItem) {
            declareEnemy(targetGuild)
        }
        pane.addItem(confirmGuiItem, 3, 1)

        // Cancel button
        val cancelItem = ItemStack(Material.ARROW)
            .name("§eCancel")
            .lore("§7Return to guild selection")

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
            player.sendMessage("§c⚔ ${targetGuild.name} declared as enemy!")
            player.sendMessage("§7Your guilds are now enemies.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f)

            // Notify target guild members
            notifyGuildMembers(targetGuild.id, "§c⚔ ${guild.name} §chas declared your guild as an enemy!")

            // Broadcast to all online players
            Bukkit.getOnlinePlayers().forEach { onlinePlayer ->
                if (onlinePlayer != player) {
                    onlinePlayer.sendMessage("§c⚔ §6${guild.name} §chas declared §6${targetGuild.name} §cas an enemy!")
                }
            }
        } else {
            player.sendMessage("§c✗ Failed to declare enemy status.")
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
            .lore("§7Available guilds: §f${allGuilds.size}")

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
