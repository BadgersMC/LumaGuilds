package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.BankTransaction
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.entities.War
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildStatisticsMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                         private var guild: Guild, private val messageService: MessageService): Menu, KoinComponent {

    private val killService: KillService by inject()
    private val warService: WarService by inject()
    private val memberService: MemberService by inject()
    private val bankService: BankService by inject()
    private val analyticsService: AnalyticsService by inject()
    private val mapRendererService: MapRendererService by inject()
    private val guildService: GuildService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    private val logger = LoggerFactory.getLogger(GuildStatisticsMenu::class.java)

    private val decimalFormat = DecimalFormat("#.##")

    override fun open() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold>${guild.name} - Statistics"))
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)

        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        // Row 1: Overview Statistics
        addKillStatsButton(pane, 0, 0)
        addWarStatsButton(pane, 1, 0)
        addMemberStatsButton(pane, 2, 0)
        addPerformanceButton(pane, 3, 0)

        // Row 2: Rankings and Top Performers
        addTopKillersButton(pane, 0, 1)
        addTopContributorsButton(pane, 1, 1)
        addKillDeathRatiosButton(pane, 2, 1)
        addRecentActivityButton(pane, 3, 1)

        // Row 2.5: Analytics Dashboard
        addAnalyticsDashboardButton(pane, 4, 1)

        // Row 3: Enhanced Analytics
        addGuildPerformanceButton(pane, 5, 2)
        addMemberAnalyticsButton(pane, 6, 2)
        addBankAnalyticsButton(pane, 7, 2)
        addWarAnalyticsButton(pane, 8, 2)

        // Row 3: Advanced Analytics
        addPeriodStatsButton(pane, 0, 2)
        addRivalryStatsButton(pane, 1, 2)
        addAchievementsButton(pane, 3, 2)

        // Row 4: Visualizations
        addGraphPlaceholderButton(pane, 0, 3)
        addTrendAnalysisButton(pane, 1, 3)
        addComparisonButton(pane, 2, 3)
        addExportStatsButton(pane, 3, 3)

        // Row 5: Navigation
        addRefreshStatsButton(pane, 0, 4)
        addBackButton(pane, 7, 4)

        gui.show(player)
    }

    private fun addKillStatsButton(pane: StaticPane, x: Int, y: Int) {
        val killStats = killService.getGuildKillStats(guild.id)

        val item = ItemStack(Material.DIAMOND_SWORD)
            .setAdventureName(player, messageService, "<dark_red>Kill Statistics")
            .addAdventureLore(player, messageService, "<gray>Total Kills: <white>${killStats.totalKills}")
            .addAdventureLore(player, messageService, "<gray>Total Deaths: <white>${killStats.totalDeaths}")
            .lore("<gray>Net Kills: §${if (killStats.netKills >= 0) "a" else "c"}${killStats.netKills}")
            .addAdventureLore(player, messageService, "<gray>K/D Ratio: <yellow>${decimalFormat.format(killStats.killDeathRatio)}")
            .lore("")
            .addAdventureLore(player, messageService, "<gray>Click for detailed breakdown")

        val guiItem = GuiItem(item) {
            openKillStatsDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addWarStatsButton(pane: StaticPane, x: Int, y: Int) {
        try {
            val wars: List<War> = warService.getWarsForGuild(guild.id)
            val activeWars = wars.filter { war: War -> war.isActive }
            val warHistory: List<War> = warService.getWarHistory(guild.id, 50)

            val wins = warHistory.count { war: War -> war.winner == guild.id }
            val losses = warHistory.count { war: War -> war.winner != null && war.winner != guild.id }
            val draws = warHistory.count { war: War -> war.winner == null }

            val item = ItemStack(Material.WHITE_BANNER)
                .setAdventureName(player, messageService, "<dark_red>War Statistics")
                .addAdventureLore(player, messageService, "<gray>Active Wars: <white>${activeWars.size}")
                .addAdventureLore(player, messageService, "<gray>Total Wars: <white>${warHistory.size}")
                .addAdventureLore(player, messageService, "<gray>Wins: <green>$wins")
                .addAdventureLore(player, messageService, "<gray>Losses: <red>$losses")
                .addAdventureLore(player, messageService, "<gray>Draws: <yellow>$draws")
                .lore("")
                .addAdventureLore(player, messageService, "<gray>Win Rate: <yellow>${decimalFormat.format(calculateWinRate(wins, warHistory.size))}%")

            val guiItem = GuiItem(item) {
                openWarStatsDetail()
            }
            pane.addItem(guiItem, x, y)
        } catch (e: Exception) {
            // Fallback to placeholder if war service fails
            val item = ItemStack(Material.WHITE_BANNER)
                .setAdventureName(player, messageService, "<dark_red>War Statistics")
                .addAdventureLore(player, messageService, "<gray>Active Wars: <white>0")
                .addAdventureLore(player, messageService, "<gray>Total Wars: <white>0")
                .addAdventureLore(player, messageService, "<gray>Wins: <green>0")
                .addAdventureLore(player, messageService, "<gray>Losses: <red>0")
                .addAdventureLore(player, messageService, "<gray>Draws: <yellow>0")
                .lore("")
                .addAdventureLore(player, messageService, "<gray>Win Rate: <yellow>0.0%")
                .addAdventureLore(player, messageService, "<red>War system not available")

            val guiItem = GuiItem(item) {
                openWarStatsDetail()
            }
            pane.addItem(guiItem, x, y)
        }
    }

    private fun addMemberStatsButton(pane: StaticPane, x: Int, y: Int) {
        val memberCount = memberService.getMemberCount(guild.id)
        val onlineMembers = memberService.getOnlineMembers(guild.id).size

        val item = ItemStack(Material.PLAYER_HEAD)
            .setAdventureName(player, messageService, "<aqua>Member Statistics")
            .addAdventureLore(player, messageService, "<gray>Total Members: <white>$memberCount")
            .addAdventureLore(player, messageService, "<gray>Online Now: <green>$onlineMembers")
            .addAdventureLore(player, messageService, "<gray>Offline: <gray>${memberCount - onlineMembers}")
            .lore("")
            .addAdventureLore(player, messageService, "<gray>Activity Rate: <yellow>${calculateActivityRate(memberCount, onlineMembers)}%")

        val guiItem = GuiItem(item) {
            openMemberStatsDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addPerformanceButton(pane: StaticPane, x: Int, y: Int) {
        val killStats = killService.getGuildKillStats(guild.id)
        val memberCount = memberService.getMemberCount(guild.id)

        val avgKillsPerMember = if (memberCount > 0) killStats.totalKills.toDouble() / memberCount else 0.0
        val avgDeathsPerMember = if (memberCount > 0) killStats.totalDeaths.toDouble() / memberCount else 0.0

        val item = ItemStack(Material.EXPERIENCE_BOTTLE)
            .setAdventureName(player, messageService, "<yellow>Performance Metrics")
            .addAdventureLore(player, messageService, "<gray>Avg Kills/Member: <white>${decimalFormat.format(avgKillsPerMember)}")
            .addAdventureLore(player, messageService, "<gray>Avg Deaths/Member: <white>${decimalFormat.format(avgDeathsPerMember)}")
            .addAdventureLore(player, messageService, "<gray>Kill Efficiency: <yellow>${calculateEfficiency(killStats)}%")
            .lore("")
            .addAdventureLore(player, messageService, "<gray>Overall Rating: §${getPerformanceColor(killStats, memberCount)}${getPerformanceRating(killStats, memberCount)}")

        val guiItem = GuiItem(item) {
            openPerformanceDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addGraphPlaceholderButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack(Material.FILLED_MAP)
            .setAdventureName(player, messageService, "<dark_purple>📊 Visual Charts")
            .addAdventureLore(player, messageService, "<gray>Interactive charts & graphs")
            .addAdventureLore(player, messageService, "<gray>Kill trends over time")
            .addAdventureLore(player, messageService, "<gray>Performance visualizations")
            .lore("")
            .addAdventureLore(player, messageService, "<yellow>Click to view guild balance chart")
            .addAdventureLore(player, messageService, "<gray>Advanced map-based rendering")

        val guiItem = GuiItem(item) {
            renderGuildBalanceChart()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<red>Back to Control Panel")
            .addAdventureLore(player, messageService, "<gray>Return to guild management")

        val guiItem = GuiItem(item) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    // Helper functions for calculations and ratings
    private fun calculateWinRate(wins: Int, totalWars: Int): Double {
        return if (totalWars > 0) (wins.toDouble() / totalWars) * 100 else 0.0
    }

    private fun calculateActivityRate(totalMembers: Int, onlineMembers: Int): String {
        return if (totalMembers > 0) decimalFormat.format((onlineMembers.toDouble() / totalMembers) * 100) else "0"
    }

    private fun calculateEfficiency(killStats: GuildKillStats): String {
        val totalActions = killStats.totalKills + killStats.totalDeaths
        return if (totalActions > 0) decimalFormat.format((killStats.totalKills.toDouble() / totalActions) * 100) else "0"
    }

    private fun getPerformanceColor(killStats: GuildKillStats, memberCount: Int): String {
        val avgKills = if (memberCount > 0) killStats.totalKills.toDouble() / memberCount else 0.0
        return when {
            avgKills >= 50 -> "a" // Green
            avgKills >= 25 -> "e" // Yellow
            avgKills >= 10 -> "6" // Gold
            else -> "c" // Red
        }
    }

    private fun getPerformanceRating(killStats: GuildKillStats, memberCount: Int): String {
        val avgKills = if (memberCount > 0) killStats.totalKills.toDouble() / memberCount else 0.0
        return when {
            avgKills >= 100 -> "Legendary"
            avgKills >= 50 -> "Elite"
            avgKills >= 25 -> "Veteran"
            avgKills >= 10 -> "Skilled"
            avgKills >= 5 -> "Novice"
            else -> "Recruit"
        }
    }

    // Detail view functions (placeholders for now)
    private fun openKillStatsDetail() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>🔪 Detailed kill statistics coming soon!")
    }

    private fun openWarStatsDetail() {
        try {
            val activeWars = warService.getWarsForGuild(guild.id).filter { it.isActive }
            val warHistory = warService.getWarHistory(guild.id, 20)

            val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<dark_red>⚔️ ${guild.name} - War Details"))
            val pane = StaticPane(0, 0, 9, 6)
            gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
            gui.setOnBottomClick { guiEvent ->
                if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                    guiEvent.isCancelled = true
                }
            }
            gui.addPane(pane)

            // Active Wars Section
            addActiveWarsSection(pane)

            // War History Section
            addWarHistorySection(pane)

            // War Statistics Section
            addWarStatisticsSection(pane)

            // Navigation
            addBackButton(pane, 8, 5)

            gui.show(player)
        } catch (e: Exception) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to load war statistics: ${e.message}")
            logger.error("Error opening war stats detail for guild ${guild.id}", e)
        }
    }

    private fun addActiveWarsSection(pane: StaticPane) {
        val activeWars = warService.getWarsForGuild(guild.id).filter { it.isActive }

        val activeWarsItem = ItemStack(Material.DIAMOND_SWORD)
            .setAdventureName(player, messageService, "<dark_red>⚔️ ACTIVE WARS (${activeWars.size})")
            .addAdventureLore(player, messageService, "<gray>Currently ongoing conflicts")

        if (activeWars.isNotEmpty()) {
            activeWarsItem.lore("")
            activeWars.take(3).forEach { war ->
                val enemyGuild = guildService.getGuild(
                    if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
                )
                val enemyName = enemyGuild?.name ?: "Unknown Guild"
                val status = if (war.declaringGuildId == guild.id) "Attacker" else "Defender"
                val remainingTime = war.remainingDuration

                activeWarsItem.addAdventureLore(player, messageService, "<red>⚔️ vs $enemyName ($status)")
                if (remainingTime != null) {
                    val days = remainingTime.toDays()
                    val hours = remainingTime.toHours() % 24
                    activeWarsItem.addAdventureLore(player, messageService, "<gray>⏰ ${days}d ${hours}h remaining")
                } else {
                    activeWarsItem.addAdventureLore(player, messageService, "<gray>⏰ Time expired")
                }
            }

            if (activeWars.size > 3) {
                activeWarsItem.addAdventureLore(player, messageService, "<gray>... and ${activeWars.size - 3} more")
            }
        } else {
            activeWarsItem.addAdventureLore(player, messageService, "<gray>No active wars")
        }

        pane.addItem(GuiItem(activeWarsItem), 1, 0)
    }

    private fun addWarHistorySection(pane: StaticPane) {
        val warHistory = warService.getWarHistory(guild.id, 5)

        val historyItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<gold>📜 WAR HISTORY")
            .addAdventureLore(player, messageService, "<gray>Recent completed wars")

        if (warHistory.isNotEmpty()) {
            historyItem.lore("")

            warHistory.take(4).forEachIndexed { index, war ->
                val enemyGuild = guildService.getGuild(
                    if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
                )
                val enemyName = enemyGuild?.name ?: "Unknown Guild"

                val result = when {
                    war.winner == guild.id -> "<green>✓ Won"
                    war.winner != null -> "<red>✗ Lost"
                    else -> "<yellow>⚖️ Draw"
                }

                historyItem.addAdventureLore(player, messageService, "§${index + 1}. vs $enemyName: $result")

                // Show duration if available
                val duration = war.startedAt?.let { start ->
                    war.endedAt?.let { end ->
                        java.time.Duration.between(start, end)
                    }
                }

                if (duration != null) {
                    val days = duration.toDays()
                    val hours = duration.toHours() % 24
                    historyItem.addAdventureLore(player, messageService, "<gray>   Duration: ${days}d ${hours}h")
                }
            }

            if (warHistory.size > 4) {
                historyItem.addAdventureLore(player, messageService, "<gray>... and ${warHistory.size - 4} more wars")
            }
        } else {
            historyItem.addAdventureLore(player, messageService, "<gray>No war history available")
        }

        pane.addItem(GuiItem(historyItem), 3, 0)
    }

    private fun addWarStatisticsSection(pane: StaticPane) {
        val warHistory = warService.getWarHistory(guild.id, 50)
        val wins = warHistory.count { it.winner == guild.id }
        val losses = warHistory.count { it.winner != null && it.winner != guild.id }
        val draws = warHistory.count { it.winner == null }

        val winRate = calculateWinRate(wins, warHistory.size)

        val statsItem = ItemStack(Material.TOTEM_OF_UNDYING)
            .setAdventureName(player, messageService, "<yellow>📊 WAR STATISTICS")
            .addAdventureLore(player, messageService, "<gray>Overall Performance")
            .lore("")
            .addAdventureLore(player, messageService, "<gray>Total Wars: <white>${warHistory.size}")
            .addAdventureLore(player, messageService, "<gray>Wins: <green>$wins")
            .addAdventureLore(player, messageService, "<gray>Losses: <red>$losses")
            .addAdventureLore(player, messageService, "<gray>Draws: <yellow>$draws")
            .lore("")
            .lore("<gray>Win Rate: <yellow>${String.format("%.1f", winRate)}%")

        // Add current win streak
        val recentWars = warHistory.take(10)
        val currentStreak = calculateCurrentStreak(recentWars, guild.id)
        if (currentStreak > 0) {
            val streakType = if (recentWars.first().winner == guild.id) "<green>Win" else "<red>Loss"
            statsItem.addAdventureLore(player, messageService, "<gray>Current Streak: ${streakType} <white>x$currentStreak")
        }

        pane.addItem(GuiItem(statsItem), 5, 0)

        // Kill Statistics During Wars
        addWarKillStats(pane)
    }

    private fun addWarKillStats(pane: StaticPane) {
        val activeWars = warService.getWarsForGuild(guild.id).filter { it.isActive }

        if (activeWars.isNotEmpty()) {
            val warKillStats = ItemStack(Material.IRON_SWORD)
                .setAdventureName(player, messageService, "<red>⚔️ CURRENT WAR KILLS")
                .addAdventureLore(player, messageService, "<gray>Kill statistics in active wars")

            // Get kill stats for each active war
            activeWars.forEach { war ->
                val enemyGuildId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
                val enemyGuild = guildService.getGuild(enemyGuildId)

                if (enemyGuild != null) {
                    val killsBetween = killService.getKillsBetweenGuilds(guild.id, enemyGuildId, 100)

                    val guildKills = killsBetween.count { it.killerGuildId == guild.id }
                    val enemyKills = killsBetween.count { it.killerGuildId == enemyGuildId }

                    warKillStats.lore("")
                        .addAdventureLore(player, messageService, "<red>⚔️ vs ${enemyGuild.name}:")
                        .addAdventureLore(player, messageService, "<gray>   Your kills: <green>$guildKills")
                        .addAdventureLore(player, messageService, "<gray>   Enemy kills: <red>$enemyKills")
                        .addAdventureLore(player, messageService, "<gray>   Ratio: <yellow>${calculateKillRatio(guildKills, enemyKills)}")
                }
            }

            pane.addItem(GuiItem(warKillStats), 7, 0)
        }
    }

    private fun calculateCurrentStreak(wars: List<War>, guildId: UUID): Int {
        if (wars.isEmpty()) return 0

        var streak = 0
        val firstWarWinner = wars.first().winner

        if (firstWarWinner == guildId || firstWarWinner == null) {
            streak = 1
            for (war in wars.drop(1)) {
                if (war.winner == firstWarWinner) {
                    streak++
                } else {
                    break
                }
            }
        }

        return streak
    }

    private fun calculateKillRatio(guildKills: Int, enemyKills: Int): String {
        return when {
            enemyKills == 0 -> if (guildKills > 0) "∞" else "0.00"
            else -> String.format("%.2f", guildKills.toDouble() / enemyKills.toDouble())
        }
    }

    private fun openMemberStatsDetail() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>👥 Detailed member statistics coming soon!")
    }

    private fun openPerformanceDetail() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>📊 Detailed performance analysis coming soon!")
    }

    private fun addTopKillersButton(pane: StaticPane, x: Int, y: Int) {
        val guildMembers = memberService.getGuildMembers(guild.id).map { it.playerId }
        val topKillers = killService.getTopKillers(guildMembers, 5)

        val item = ItemStack(Material.TOTEM_OF_UNDYING)
            .setAdventureName(player, messageService, "<red>Top Killers")
            .addAdventureLore(player, messageService, "<gray>Guild's elite warriors")

        if (topKillers.isNotEmpty()) {
            item.lore("")
            topKillers.take(3).forEachIndexed { index, (playerId, stats) ->
                val playerName = Bukkit.getPlayer(playerId)?.name ?: "Unknown"
                item.addAdventureLore(player, messageService, "§${getRankColor(index + 1)}${index + 1}. $playerName: <white>${stats.totalKills} kills")
            }
        } else {
            item.addAdventureLore(player, messageService, "<gray>No kill data available")
        }

        val guiItem = GuiItem(item) {
            openTopKillersDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addTopContributorsButton(pane: StaticPane, x: Int, y: Int) {
        val contributions = bankService.getMemberContributions(guild.id)
        val topContributors = contributions
            .filter { it.netContribution > 0 }
            .sortedByDescending { it.netContribution }
            .take(3)

        val item = ItemStack(Material.GOLD_BLOCK)
            .setAdventureName(player, messageService, "<gold>Top Contributors")
            .addAdventureLore(player, messageService, "<gray>Most generous members")

        if (topContributors.isNotEmpty()) {
            item.lore("")
            topContributors.forEachIndexed { index, contribution ->
                val playerName = contribution.playerName ?: "Unknown"
                item.addAdventureLore(player, messageService, "§${getRankColor(index + 1)}${index + 1}. $playerName: <white>$${contribution.netContribution}")
            }
        } else {
            item.addAdventureLore(player, messageService, "<gray>No contribution data")
        }

        val guiItem = GuiItem(item) {
            openTopContributorsDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addKillDeathRatiosButton(pane: StaticPane, x: Int, y: Int) {
        val killStats = killService.getGuildKillStats(guild.id)

        val item = ItemStack(Material.COMPARATOR)
            .setAdventureName(player, messageService, "<light_purple>K/D Analysis")
            .addAdventureLore(player, messageService, "<gray>Kill/Death Ratio: <yellow>${decimalFormat.format(killStats.killDeathRatio)}")
            .addAdventureLore(player, messageService, "<gray>Performance Grade: §${getKDRatingColor(killStats.killDeathRatio)}${getKDRating(killStats.killDeathRatio)}")
            .lore("")
            .addAdventureLore(player, messageService, "<gray>Efficiency Score: <white>${calculateEfficiencyScore(killStats)}/100")

        val guiItem = GuiItem(item) {
            openKDAnalysisDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addRecentActivityButton(pane: StaticPane, x: Int, y: Int) {
        val recentKills = killService.getRecentGuildKills(guild.id, 10)
        val recentActivity = recentKills.size

        val item = ItemStack(Material.CLOCK)
            .setAdventureName(player, messageService, "<white>Recent Activity")
            .addAdventureLore(player, messageService, "<gray>Last 24 hours")
            .addAdventureLore(player, messageService, "<gray>Kills: <white>$recentActivity")
            .addAdventureLore(player, messageService, "<gray>Activity Level: §${getActivityColor(recentActivity)}${getActivityLevel(recentActivity)}")

        val guiItem = GuiItem(item) {
            openRecentActivityDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addPeriodStatsButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<dark_aqua>Period Statistics")
            .addAdventureLore(player, messageService, "<gray>View stats by time period")
            .addAdventureLore(player, messageService, "<gray>Daily, Weekly, Monthly")
            .addAdventureLore(player, messageService, "<gray>Compare performance over time")

        val guiItem = GuiItem(item) {
            openPeriodStatsMenu()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addRivalryStatsButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack(Material.RED_BANNER)
            .setAdventureName(player, messageService, "<dark_red>Rivalry Statistics")
            .addAdventureLore(player, messageService, "<gray>Kills vs other guilds")
            .addAdventureLore(player, messageService, "<gray>Dominance rankings")
            .addAdventureLore(player, messageService, "<gray>Most aggressive rivals")

        val guiItem = GuiItem(item) {
            openRivalryStatsDetail()
        }
        pane.addItem(guiItem, x, y)
    }


    private fun addAchievementsButton(pane: StaticPane, x: Int, y: Int) {
        val killStats = killService.getGuildKillStats(guild.id)
        val achievementCount = calculateAchievementCount(killStats)

        val item = ItemStack(Material.TROPICAL_FISH_BUCKET)
            .setAdventureName(player, messageService, "<yellow>Guild Achievements")
            .addAdventureLore(player, messageService, "<gray>Milestones unlocked: <white>$achievementCount")
            .addAdventureLore(player, messageService, "<gray>Total Kills: §${getAchievementColor(killStats.totalKills)}${getKillMilestone(killStats.totalKills)}")
            .addAdventureLore(player, messageService, "<gray>Net Kills: §${getAchievementColor(killStats.netKills)}${getNetKillMilestone(killStats.netKills)}")

        val guiItem = GuiItem(item) {
            openAchievementsDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addTrendAnalysisButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack(Material.REPEATER)
            .setAdventureName(player, messageService, "<dark_aqua>📈 Kill Trends")
            .addAdventureLore(player, messageService, "<gray>Kill performance over time")
            .addAdventureLore(player, messageService, "<gray>Track improvement patterns")
            .addAdventureLore(player, messageService, "<gray>Interactive trend chart")

        val guiItem = GuiItem(item) {
            renderKillTrendChart()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addComparisonButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack(Material.COMPARATOR)
            .setAdventureName(player, messageService, "<blue>📊 Member Contributions")
            .addAdventureLore(player, messageService, "<gray>Compare member contributions")
            .addAdventureLore(player, messageService, "<gray>Visual ranking chart")
            .addAdventureLore(player, messageService, "<gray>Identify top contributors")

        val guiItem = GuiItem(item) {
            renderMemberContributionsChart()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addExportStatsButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack(Material.WRITABLE_BOOK)
            .setAdventureName(player, messageService, "<white>Export Statistics")
            .addAdventureLore(player, messageService, "<gray>Download detailed stats")
            .addAdventureLore(player, messageService, "<gray>CSV format for analysis")
            .addAdventureLore(player, messageService, "<gray>Secure file delivery")

        val guiItem = GuiItem(item) {
            exportGuildStatistics()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addRefreshStatsButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack(Material.KNOWLEDGE_BOOK)
            .setAdventureName(player, messageService, "<green>Refresh Statistics")
            .addAdventureLore(player, messageService, "<gray>Update all statistics")
            .addAdventureLore(player, messageService, "<gray>Fetch latest data")

        val guiItem = GuiItem(item) {
            AdventureMenuHelper.sendMessage(player, messageService, "<green>🔄 Refreshing statistics...")
            // Reopen the menu to refresh all data
            open()
        }
        pane.addItem(guiItem, x, y)
    }

    /**
     * Add analytics dashboard button
     */
    private fun addAnalyticsDashboardButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack(Material.ENCHANTED_BOOK)
            .setAdventureName(player, messageService, "<gold>📊 Analytics Dashboard")
            .addAdventureLore(player, messageService, "<gray>Comprehensive guild analytics")
            .addAdventureLore(player, messageService, "<gray>Performance metrics & insights")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>• Guild Performance")
            .addAdventureLore(player, messageService, "<gray>• Member Activity")
            .addAdventureLore(player, messageService, "<gray>• Bank Analytics")
            .addAdventureLore(player, messageService, "<gray>• War Statistics")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>Click to view dashboard")

        val guiItem = GuiItem(item) {
            openAnalyticsDashboard()
        }
        pane.addItem(guiItem, x, y)
    }

    /**
     * Add guild performance analytics button
     */
    private fun addGuildPerformanceButton(pane: StaticPane, x: Int, y: Int) {
        val metrics = analyticsService.getGuildPerformanceMetrics(guild.id, TimePeriod.LAST_30_DAYS)

        val item = ItemStack(Material.DIAMOND)
            .setAdventureName(player, messageService, "<gold>🏆 Guild Performance")
            .addAdventureLore(player, messageService, "<gray>Last 30 days overview")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Members: <white>${metrics.totalMembers}")
            .addAdventureLore(player, messageService, "<gray>Active: <green>${metrics.activeMembers}")
            .addAdventureLore(player, messageService, "<gray>New: <yellow>${metrics.newMembers}")
            .addAdventureLore(player, messageService, "<gray>Bank Growth: <green>${decimalFormat.format(metrics.monthlyBankGrowth)}%")
            .addAdventureLore(player, messageService, "<gray>Wars Won: <white>${metrics.warsWon}")
            .addAdventureLore(player, messageService, "<gray>Activity Score: <yellow>${decimalFormat.format(metrics.activityScore)}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>Click for detailed view")

        val guiItem = GuiItem(item) {
            openGuildPerformanceDetails()
        }
        pane.addItem(guiItem, x, y)
    }

    /**
     * Add member analytics button
     */
    private fun addMemberAnalyticsButton(pane: StaticPane, x: Int, y: Int) {
        val analytics = analyticsService.getMemberActivityAnalytics(guild.id, TimePeriod.LAST_30_DAYS)

        val item = ItemStack(Material.PLAYER_HEAD)
            .setAdventureName(player, messageService, "<gold>👥 Member Analytics")
            .addAdventureLore(player, messageService, "<gray>Member engagement analysis")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Total Members: <white>${analytics.totalMembers}")
            .addAdventureLore(player, messageService, "<gray>Active Members: <green>${analytics.activeMembers}")
            .addAdventureLore(player, messageService, "<gray>Inactive: <red>${analytics.inactiveMembers}")
            .addAdventureLore(player, messageService, "<gray>Avg Activity: <yellow>${decimalFormat.format(analytics.averageActivityScore)}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>Click for member insights")

        val guiItem = GuiItem(item) {
            openMemberAnalyticsDetails()
        }
        pane.addItem(guiItem, x, y)
    }

    /**
     * Add bank analytics button
     */
    private fun addBankAnalyticsButton(pane: StaticPane, x: Int, y: Int) {
        val analytics = analyticsService.getBankAnalytics(guild.id, TimePeriod.LAST_30_DAYS)

        val item = ItemStack(Material.GOLD_INGOT)
            .setAdventureName(player, messageService, "<gold>💰 Bank Analytics")
            .addAdventureLore(player, messageService, "<gray>Financial performance analysis")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Transactions: <white>${analytics.totalTransactions}")
            .addAdventureLore(player, messageService, "<gray>Deposits: <green>${analytics.totalDeposits}")
            .addAdventureLore(player, messageService, "<gray>Withdrawals: <red>${analytics.totalWithdrawals}")
            .addAdventureLore(player, messageService, "<gray>Net Flow: <yellow>${analytics.netFlow}")
            .addAdventureLore(player, messageService, "<gray>Avg Transaction: <white>${decimalFormat.format(analytics.averageTransactionAmount)}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>Click for financial insights")

        val guiItem = GuiItem(item) {
            openBankAnalyticsDetails()
        }
        pane.addItem(guiItem, x, y)
    }

    /**
     * Add war analytics button
     */
    private fun addWarAnalyticsButton(pane: StaticPane, x: Int, y: Int) {
        val stats = analyticsService.getWarStatistics(guild.id, TimePeriod.LAST_30_DAYS)

        val item = ItemStack(Material.IRON_SWORD)
            .setAdventureName(player, messageService, "<gold>⚔️ War Analytics")
            .addAdventureLore(player, messageService, "<gray>Combat performance analysis")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Wars: <white>${stats.warsParticipated}")
            .addAdventureLore(player, messageService, "<gray>Wins: <green>${stats.warsWon}")
            .addAdventureLore(player, messageService, "<gray>Losses: <red>${stats.warsLost}")
            .addAdventureLore(player, messageService, "<gray>Win Rate: <yellow>${decimalFormat.format(stats.winRate)}%")
            .addAdventureLore(player, messageService, "<gray>K/D Ratio: <white>${decimalFormat.format(stats.kdRatio)}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>Click for war insights")

        val guiItem = GuiItem(item) {
            openWarAnalyticsDetails()
        }
        pane.addItem(guiItem, x, y)
    }

    /**
     * Open comprehensive analytics dashboard
     */
    private fun openAnalyticsDashboard() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold>Analytics Dashboard - ${guild.name}"))
        AntiDupeUtil.protect(gui)

        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        var row = 0

        // Guild Overview
        val overview = analyticsService.getGuildPerformanceMetrics(guild.id, TimePeriod.LAST_30_DAYS)
        val overviewItem = ItemStack(Material.COMMAND_BLOCK)
            .setAdventureName(player, messageService, "<gold>📊 Guild Overview")
            .addAdventureLore(player, messageService, "<gray>Performance Summary")
            .addAdventureLore(player, messageService, "<gray>Members: <white>${overview.totalMembers} (<green>${overview.activeMembers} active)")
            .addAdventureLore(player, messageService, "<gray>Bank: <green>${overview.totalBankBalance} coins")
            .addAdventureLore(player, messageService, "<gray>Growth: <yellow>${decimalFormat.format(overview.monthlyBankGrowth)}%")
            .addAdventureLore(player, messageService, "<gray>Wars: <white>${overview.warsWon}W/${overview.warsLost}L")

        pane.addItem(GuiItem(overviewItem), 4, row)

        // Analytics sections
        row = 1

        // Member Analytics
        val memberAnalytics = analyticsService.getMemberActivityAnalytics(guild.id, TimePeriod.LAST_30_DAYS)
        val memberItem = ItemStack(Material.PLAYER_HEAD)
            .setAdventureName(player, messageService, "<gold>👥 Member Analytics")
            .addAdventureLore(player, messageService, "<gray>Engagement & Activity")
            .addAdventureLore(player, messageService, "<gray>Active: <green>${memberAnalytics.activeMembers}/${memberAnalytics.totalMembers}")
            .addAdventureLore(player, messageService, "<gray>Top Contributors: <yellow>${memberAnalytics.topContributors.size}")

        pane.addItem(GuiItem(memberItem), 0, row)

        // Bank Analytics
        val bankAnalytics = analyticsService.getBankAnalytics(guild.id, TimePeriod.LAST_30_DAYS)
        val bankItem = ItemStack(Material.GOLD_INGOT)
            .setAdventureName(player, messageService, "<gold>💰 Financial Analytics")
            .addAdventureLore(player, messageService, "<gray>Transaction Analysis")
            .addAdventureLore(player, messageService, "<gray>Volume: <white>${bankAnalytics.totalTransactions}")
            .addAdventureLore(player, messageService, "<gray>Net Flow: <yellow>${bankAnalytics.netFlow}")

        pane.addItem(GuiItem(bankItem), 2, row)

        // War Analytics
        val warStats = analyticsService.getWarStatistics(guild.id, TimePeriod.LAST_30_DAYS)
        val warItem = ItemStack(Material.IRON_SWORD)
            .setAdventureName(player, messageService, "<gold>⚔️ Combat Analytics")
            .addAdventureLore(player, messageService, "<gray>War Performance")
            .addAdventureLore(player, messageService, "<gray>Record: <white>${warStats.warsWon}W/${warStats.warsLost}L")
            .addAdventureLore(player, messageService, "<gray>Win Rate: <yellow>${decimalFormat.format(warStats.winRate)}%")

        pane.addItem(GuiItem(warItem), 4, row)

        // Comparative Analysis
        val comparisonItem = ItemStack(Material.COMPARATOR)
            .setAdventureName(player, messageService, "<gold>📈 Comparative Analysis")
            .addAdventureLore(player, messageService, "<gray>Guild Rankings")
            .addAdventureLore(player, messageService, "<gray>Compare with other guilds")

        pane.addItem(GuiItem(comparisonItem), 6, row)

        row = 2

        // Trend Analysis
        val trendItem = ItemStack(Material.REPEATER)
            .setAdventureName(player, messageService, "<gold>📊 Trend Analysis")
            .addAdventureLore(player, messageService, "<gray>Performance over time")
            .addAdventureLore(player, messageService, "<gray>Growth patterns & predictions")

        pane.addItem(GuiItem(trendItem), 0, row)

        // Export Analytics
        val exportItem = ItemStack(Material.WRITABLE_BOOK)
            .setAdventureName(player, messageService, "<gold>📄 Export Analytics")
            .addAdventureLore(player, messageService, "<gray>Download detailed reports")
            .addAdventureLore(player, messageService, "<gray>CSV format for analysis")

        pane.addItem(GuiItem(exportItem), 2, row)

        // Back button
        val backItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<red>Back to Statistics")
            .addAdventureLore(player, messageService, "<gray>Return to main statistics")

        val backGuiItem = GuiItem(backItem) {
            menuNavigator.openMenu(this)
        }
        pane.addItem(backGuiItem, 8, 5)

        gui.addPane(pane)
        gui.show(player)
    }

    /**
     * Open detailed guild performance view
     */
    private fun openGuildPerformanceDetails() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Detailed guild performance view coming soon!")
    }

    /**
     * Open detailed member analytics view
     */
    private fun openMemberAnalyticsDetails() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Detailed member analytics view coming soon!")
    }

    /**
     * Open detailed bank analytics view
     */
    private fun openBankAnalyticsDetails() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Detailed bank analytics view coming soon!")
    }

    /**
     * Open detailed war analytics view
     */
    private fun openWarAnalyticsDetails() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Detailed war analytics view coming soon!")
    }

    private fun getRankColor(rank: Int): String {
        return when (rank) {
            1 -> "6" // Gold
            2 -> "7" // Gray
            3 -> "c" // Red
            else -> "f" // White
        }
    }

    private fun getKDRatingColor(kdRatio: Double): String {
        return when {
            kdRatio >= 3.0 -> "a" // Green
            kdRatio >= 1.5 -> "e" // Yellow
            kdRatio >= 1.0 -> "6" // Gold
            else -> "c" // Red
        }
    }

    private fun getKDRating(kdRatio: Double): String {
        return when {
            kdRatio >= 5.0 -> "Godlike"
            kdRatio >= 3.0 -> "Excellent"
            kdRatio >= 2.0 -> "Very Good"
            kdRatio >= 1.5 -> "Good"
            kdRatio >= 1.0 -> "Average"
            kdRatio >= 0.5 -> "Below Average"
            else -> "Needs Improvement"
        }
    }

    private fun calculateEfficiencyScore(killStats: GuildKillStats): Int {
        val kdRatio = killStats.killDeathRatio
        return when {
            kdRatio >= 3.0 -> 100
            kdRatio >= 2.0 -> 85
            kdRatio >= 1.5 -> 70
            kdRatio >= 1.0 -> 55
            kdRatio >= 0.7 -> 40
            else -> 25
        }
    }

    private fun getActivityColor(activity: Int): String {
        return when {
            activity >= 20 -> "a" // Green
            activity >= 10 -> "e" // Yellow
            activity >= 5 -> "6" // Gold
            else -> "c" // Red
        }
    }

    private fun getActivityLevel(activity: Int): String {
        return when {
            activity >= 50 -> "Extremely Active"
            activity >= 20 -> "Very Active"
            activity >= 10 -> "Active"
            activity >= 5 -> "Moderately Active"
            activity >= 1 -> "Lightly Active"
            else -> "Inactive"
        }
    }

    private fun calculateAchievementCount(killStats: GuildKillStats): Int {
        var count = 0
        if (killStats.totalKills >= 100) count++
        if (killStats.totalKills >= 500) count++
        if (killStats.totalKills >= 1000) count++
        if (killStats.netKills >= 100) count++
        if (killStats.netKills >= 500) count++
        if (killStats.killDeathRatio >= 2.0) count++
        return count
    }

    private fun getAchievementColor(value: Int): String {
        return when {
            value >= 1000 -> "6" // Gold
            value >= 500 -> "e" // Yellow
            value >= 100 -> "a" // Green
            value >= 50 -> "b" // Aqua
            else -> "f" // White
        }
    }

    private fun getKillMilestone(kills: Int): String {
        return when {
            kills >= 10000 -> "Massacre (10k+)"
            kills >= 5000 -> "Slaughter (5k+)"
            kills >= 1000 -> "Carnage (1k+)"
            kills >= 500 -> "Bloodbath (500+)"
            kills >= 100 -> "Butcher (100+)"
            else -> "Novice (<100)"
        }
    }

    private fun getNetKillMilestone(netKills: Int): String {
        return when {
            netKills >= 1000 -> "Dominator (1k+)"
            netKills >= 500 -> "Conqueror (500+)"
            netKills >= 100 -> "Warrior (100+)"
            netKills >= 0 -> "Balanced (0+)"
            netKills >= -50 -> "Challenged (-50)"
            else -> "Struggling (-100)"
        }
    }

    // Additional detail view functions
    private fun openTopKillersDetail() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>🏆 Detailed top killers rankings coming soon!")
    }

    private fun openTopContributorsDetail() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>💰 Detailed contribution analysis coming soon!")
    }

    private fun openKDAnalysisDetail() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>📈 Detailed K/D ratio analysis coming soon!")
    }

    private fun openRecentActivityDetail() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>🕐 Detailed recent activity coming soon!")
    }

    private fun openPeriodStatsMenu() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>📅 Period-based statistics coming soon!")
    }

    private fun openRivalryStatsDetail() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>🏴 Rivalry statistics coming soon!")
    }


    private fun openAchievementsDetail() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>🏅 Achievement details coming soon!")
    }

    private fun openTrendAnalysis() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>📈 Trend analysis coming soon!")
    }

    private fun openGuildComparison() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>⚖️ Guild comparison coming soon!")
    }


    private fun exportGuildStatistics() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>📊 Statistics export feature coming soon!")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Will generate CSV files with all guild data")
    }

    // Chart rendering methods
    private fun renderGuildBalanceChart() {
        try {
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>📊 Generating guild balance trend chart...")

            // Get real transaction history from the database
            val transactions = bankService.getTransactionHistory(guild.id, 50)

            if (transactions.isEmpty()) {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ No transaction history found for this guild.")
                return
            }

            // Group transactions by date and calculate balance progression
            val dateToBalance = mutableMapOf<LocalDate, Int>()
            for (transaction in transactions) {
                val date = LocalDateTime.ofInstant(transaction.timestamp, ZoneId.systemDefault()).toLocalDate()
                dateToBalance[date] = (dateToBalance[date] ?: 0) + transaction.amount
            }

            val dailyBalances = dateToBalance.toList()
                .sortedBy { it.first }
                .takeLast(30) // Last 30 days
                .map { it.first.toString() to it.second }

            if (dailyBalances.isEmpty()) {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Unable to process transaction data.")
                return
            }

            val chart = mapRendererService.renderCustomChart(
                title = "${guild.name} Balance Trend",
                dataPoints = dailyBalances,
                chartType = ChartType.LINE,
                player = player
            )

            if (chart != null) {
                player.inventory.addItem(chart)
                AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Guild balance chart generated! Hold the map to view interactive trends.")
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to generate balance chart. Please try again.")
            }

        } catch (e: Exception) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Error generating balance chart: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun renderKillTrendChart() {
        try {
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>📊 Generating kill trend analysis chart...")

            // Get real kill data for the past 7 weeks
            val now = Instant.now()
            val weekTrends = mutableListOf<Pair<String, Int>>()

            for (weeksAgo in 6 downTo 0) {
                val weekStart = now.minusSeconds(weeksAgo * 7L * 24L * 60L * 60L)
                val weekEnd = weekStart.plusSeconds(7L * 24L * 60L * 60L)

                try {
                    val weekStats = killService.getKillStatsForPeriod(guild.id, weekStart, weekEnd)
                    val weekLabel = if (weeksAgo == 0) "This Week" else "${weeksAgo}W ago"
                    weekTrends.add(weekLabel to weekStats.totalKills)
                } catch (e: Exception) {
                    // If we can't get data for this week, use 0
                    val weekLabel = if (weeksAgo == 0) "This Week" else "${weeksAgo}W ago"
                    weekTrends.add(weekLabel to 0)
                }
            }

            // Reverse to show chronological order
            weekTrends.reverse()

            if (weekTrends.all { it.second == 0 }) {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ No kill data found for this guild in the past 7 weeks.")
                return
            }

            val chart = mapRendererService.renderCustomChart(
                title = "${guild.name} Kill Trends",
                dataPoints = weekTrends,
                chartType = ChartType.LINE,
                player = player
            )

            if (chart != null) {
                player.inventory.addItem(chart)
                AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Kill trend chart generated! Hold the map to view performance patterns.")
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to generate kill trend chart. Please try again.")
            }

        } catch (e: Exception) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Error generating kill trend chart: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun renderMemberContributionsChart() {
        try {
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>📊 Generating member contributions chart...")

            // Get real member contribution data from BankService
            val contributions = bankService.getMemberContributions(guild.id)

            if (contributions.isEmpty()) {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ No contribution data found for this guild.")
                return
            }

            // Convert to chart data format with player names
            val chartData = contributions
                .filter { it.netContribution > 0 }
                .sortedByDescending { it.netContribution }
                .take(10) // Top 10 contributors
                .map { (it.playerName ?: "Unknown") to it.netContribution }

            if (chartData.isEmpty()) {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ No positive contributions found for this guild.")
                return
            }

            val chart = mapRendererService.renderCustomChart(
                title = "${guild.name} Member Contributions",
                dataPoints = chartData,
                chartType = ChartType.BAR,
                player = player
            )

            if (chart != null) {
                player.inventory.addItem(chart)
                AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Member contributions chart generated! Hold the map to view ranking visualization.")
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to generate contributions chart. Please try again.")
            }

        } catch (e: Exception) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Error generating contributions chart: ${e.message}")
            e.printStackTrace()
        }
    }}

