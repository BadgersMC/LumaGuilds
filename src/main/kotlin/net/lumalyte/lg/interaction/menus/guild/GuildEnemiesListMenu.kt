package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.War
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.min
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildEnemiesListMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                          private val guild: Guild) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val warService: WarService by inject()

    private var currentPage = 0
    private val enemiesPerPage = 8
    private var activeWars: List<War> = emptyList()

    override fun open() {
        // Load active wars for this guild
        activeWars = loadActiveWars(guild.id)

        val gui = ChestGui(5, "§cEnemies List - ${guild.name}")
        val mainPane = StaticPane(0, 0, 9, 4)
        val navigationPane = StaticPane(0, 4, 9, 1)

        // Load enemies into main pane (simplified for now)
        loadEnemiesPage(mainPane)

        // Add navigation
        addNavigation(navigationPane)

        gui.addPane(mainPane)
        gui.addPane(navigationPane)

        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun loadActiveWars(guildId: UUID): List<War> {
        // Get wars where this guild is either declaring or defending
        val wars = warService.getWarsForGuild(guildId)
        return wars.filter { war: War -> war.isActive }
    }

    private fun loadEnemiesPage(mainPane: StaticPane) {
        // For now, show first 36 enemies (4 rows x 9 columns)
        val maxEnemies = min(36, activeWars.size)
        val pageWars = activeWars.take(maxEnemies)

        pageWars.forEachIndexed { index, war ->
            // Determine the enemy guild (the one that's not the current guild)
            val enemyGuildId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
            val enemyGuild = guildService.getGuild(enemyGuildId)

            if (enemyGuild != null) {
                val item = createEnemyItem(enemyGuild, war)
                mainPane.addItem(GuiItem(item), index % 9, index / 9)
            }
        }
    }

    private fun createEnemyItem(enemyGuild: Guild, war: War): ItemStack {
        val declaredAt = war.declaredAt.atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
        val warStatus = when {
            war.isActive -> "§cActive"
            war.isEnded -> "§aEnded"
            else -> "§eUnknown"
        }

        val lore = listOf(
            "§7Owner: §f${enemyGuild.ownerName}",
            "§7War Started: §f$declaredAt",
            "§7Status: $warStatus",
            "§7Your Role: §f${if (war.declaringGuildId == guild.id) "Attacker" else "Defender"}",
            "§eClick to manage war or negotiate peace"
        )

        return ItemStack(Material.PLAYER_HEAD)
            .name("§c${enemyGuild.name}")
            .lore(lore)
    }

    private fun addNavigation(navigationPane: StaticPane) {
        // Previous page
        if (currentPage > 0) {
            val prevItem = ItemStack(Material.ARROW).name("§aPrevious Page").lore(listOf("§7Click to go back"))
            navigationPane.addItem(GuiItem(prevItem) { _ ->
                currentPage--
                open() // Reopen with new page
            }, 0, 0)
        }

        // Next page
        if ((currentPage + 1) * enemiesPerPage < activeWars.size) {
            val nextItem = ItemStack(Material.ARROW).name("§aNext Page").lore(listOf("§7Click to go forward"))
            navigationPane.addItem(GuiItem(nextItem) { _ ->
                currentPage++
                open() // Reopen with new page
            }, 8, 0)
        }

        // Back to Relations Hub
        val backItem = ItemStack(Material.BARRIER).name("§cBack to Relations").lore(listOf("§7Return to relations menu"))
        navigationPane.addItem(GuiItem(backItem) { _ ->
            menuNavigator.goBack()
        }, 4, 0)
    }

    private fun manageWar(enemyGuild: Guild, war: War) {
        // For now, open a simple management menu
        // In the future, this could open the advanced war management menu
        openWarManagementOptions(enemyGuild, war)
    }

    private fun openWarManagementOptions(enemyGuild: Guild, war: War) {
        val gui = ChestGui(3, "War Management - ${enemyGuild.name}")
        val pane = StaticPane(0, 0, 9, 3)

        // War Statistics
        val statsItem = ItemStack(Material.KNOWLEDGE_BOOK).name("§bWar Statistics")
            .lore(listOf(
                "§7View detailed war statistics",
                "§7Kills, deaths, claims captured/lost",
                "§7War objectives progress"
            ))
        pane.addItem(GuiItem(statsItem) { _ ->
            openWarStats(enemyGuild, war)
        }, 2, 1)

        // Negotiate Peace
        val peaceItem = ItemStack(Material.WHITE_BANNER).name("§aNegotiate Peace")
            .lore(listOf(
                "§7Propose peace terms",
                "§7End the war peacefully",
                "§7Requires mutual agreement"
            ))
        pane.addItem(GuiItem(peaceItem) { _ ->
            openPeaceNegotiation(enemyGuild, war)
        }, 4, 1)

        // Surrender
        val surrenderItem = ItemStack(Material.RED_BANNER).name("§cSurrender")
            .lore(listOf(
                "§7Surrender to end the war",
                "§7Accept defeat",
                "§7Requires confirmation"
            ))
        pane.addItem(GuiItem(surrenderItem) { _ ->
            openSurrenderConfirmation(enemyGuild, war)
        }, 6, 1)

        // Back
        val backItem = ItemStack(Material.ARROW).name("§eBack")
            .lore(listOf("§7Return to enemies list"))
        pane.addItem(GuiItem(backItem) { _ ->
            player.closeInventory()
        }, 4, 2)

        gui.addPane(pane)
        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun openWarStats(enemyGuild: Guild, war: War) {
        player.sendMessage("§eWar statistics feature coming soon!")
        player.sendMessage("§7This would show detailed war statistics for the conflict with ${enemyGuild.name}")
    }

    private fun openPeaceNegotiation(enemyGuild: Guild, war: War) {
        player.sendMessage("§ePeace negotiation feature coming soon!")
        player.sendMessage("§7This would allow you to propose peace terms to ${enemyGuild.name}")
    }

    private fun openSurrenderConfirmation(enemyGuild: Guild, war: War) {
        val gui = ChestGui(3, "Surrender Confirmation")
        val pane = StaticPane(0, 0, 9, 3)

        // Confirmation message
        val confirmItem = ItemStack(Material.LIME_WOOL).name("§aConfirm Surrender")
            .lore(listOf("§7Are you sure you want to surrender to ${enemyGuild.name}?"))
            .lore(listOf("§7This will end the war immediately."))
        pane.addItem(GuiItem(confirmItem) { _ ->
            // Perform the surrender action
            performSurrender(enemyGuild, war)
            player.sendMessage("§cYou have surrendered to ${enemyGuild.name}.")
            player.closeInventory()
        }, 3, 1)

        // Cancel option
        val cancelItem = ItemStack(Material.RED_WOOL).name("§cCancel")
            .lore(listOf("§7Go back to war management."))
        pane.addItem(GuiItem(cancelItem) { _ ->
            player.closeInventory()
        }, 5, 1)

        gui.addPane(pane)
        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun performSurrender(enemyGuild: Guild, war: War) {
        // Determine winner (the enemy guild)
        val winnerGuildId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId

        val success = warService.endWar(war.id, winnerGuildId, "Enemy guild surrendered", player.uniqueId)
        if (success) {
            player.sendMessage("§cWar with ${enemyGuild.name} has ended. You have surrendered.")
        } else {
            player.sendMessage("§cFailed to process surrender. Please try again.")
        }
    }
}
