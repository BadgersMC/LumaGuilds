package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.DecimalFormat
import java.util.*

class GuildStatisticsMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                         private var guild: Guild): Menu, KoinComponent {

    private val killService: KillService by inject()
    private val warService: WarService by inject()
    private val memberService: MemberService by inject()
    private val bankService: BankService by inject()
    private val mapRendererService: MapRendererService by inject()

    private val decimalFormat = DecimalFormat("#.##")

    override fun open() {
        val gui = ChestGui(6, "§6${guild.name} - Statistics")
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT)
                guiEvent.isCancelled = true
        }

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

        // Row 3: Advanced Analytics
        addPeriodStatsButton(pane, 0, 2)
        addRivalryStatsButton(pane, 1, 2)
        addActivityHeatmapButton(pane, 2, 2)
        addAchievementsButton(pane, 3, 2)

        // Row 4: Visualizations (Future)
        addGraphPlaceholderButton(pane, 0, 3)
        addTrendAnalysisButton(pane, 1, 3)
        addComparisonButton(pane, 2, 3)
        addExportStatsButton(pane, 3, 3)

        // Row 5: Navigation
        addRefreshStatsButton(pane, 0, 4)
        addDetailedViewButton(pane, 1, 4)
        addBackButton(pane, 8, 4)

        gui.show(player)
    }

    private fun addKillStatsButton(pane: StaticPane, x: Int, y: Int) {
        val killStats = killService.getGuildKillStats(guild.id)

        val item = ItemStack(Material.DIAMOND_SWORD)
            .name("§4Kill Statistics")
            .lore("§7Total Kills: §f${killStats.totalKills}")
            .lore("§7Total Deaths: §f${killStats.totalDeaths}")
            .lore("§7Net Kills: §${if (killStats.netKills >= 0) "a" else "c"}${killStats.netKills}")
            .lore("§7K/D Ratio: §e${decimalFormat.format(killStats.killDeathRatio)}")
            .lore("")
            .lore("§7Click for detailed breakdown")

        val guiItem = GuiItem(item) {
            openKillStatsDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addWarStatsButton(pane: StaticPane, x: Int, y: Int) {
        try {
            val activeWars = warService.getWarsForGuild(guild.id).filter { it.isActive }
            val warHistory = warService.getWarHistory(guild.id, 50)

            val wins = warHistory.count { it.winner == guild.id }
            val losses = warHistory.count { it.winner != null && it.winner != guild.id }
            val draws = warHistory.count { it.winner == null }

            val item = ItemStack(Material.WHITE_BANNER)
                .name("§4War Statistics")
                .lore("§7Active Wars: §f${activeWars.size}")
                .lore("§7Total Wars: §f${warHistory.size}")
                .lore("§7Wins: §a$wins")
                .lore("§7Losses: §c$losses")
                .lore("§7Draws: §e$draws")
                .lore("")
                .lore("§7Win Rate: §e${calculateWinRate(wins, warHistory.size)}%")

            val guiItem = GuiItem(item) {
                openWarStatsDetail()
            }
            pane.addItem(guiItem, x, y)
        } catch (e: Exception) {
            // Fallback to placeholder if war service fails
            val item = ItemStack(Material.WHITE_BANNER)
                .name("§4War Statistics")
                .lore("§7Active Wars: §f0")
                .lore("§7Total Wars: §f0")
                .lore("§7Wins: §a0")
                .lore("§7Losses: §c0")
                .lore("§7Draws: §e0")
                .lore("")
                .lore("§7Win Rate: §e0.0%")
                .lore("§cWar system not available")

            val guiItem = GuiItem(item) {
                openWarStatsDetail()
            }
            pane.addItem(guiItem, x, y)
        }
    }

    private fun addMemberStatsButton(pane: StaticPane, x: Int, y: Int) {
        val memberCount = memberService.getMemberCount(guild.id)
        // Placeholder for online members until MemberService is extended
        val onlineMembers = 0 // memberService.getOnlineMembers(guild.id).size

        val item = ItemStack(Material.PLAYER_HEAD)
            .name("§bMember Statistics")
            .lore("§7Total Members: §f$memberCount")
            .lore("§7Online Now: §a$onlineMembers")
            .lore("§7Offline: §7${memberCount - onlineMembers}")
            .lore("")
            .lore("§7Activity Rate: §e${calculateActivityRate(memberCount, onlineMembers)}%")
            .lore("§cOnline tracking coming soon!")

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
            .name("§ePerformance Metrics")
            .lore("§7Avg Kills/Member: §f${decimalFormat.format(avgKillsPerMember)}")
            .lore("§7Avg Deaths/Member: §f${decimalFormat.format(avgDeathsPerMember)}")
            .lore("§7Kill Efficiency: §e${calculateEfficiency(killStats)}%")
            .lore("")
            .lore("§7Overall Rating: §${getPerformanceColor(killStats, memberCount)}${getPerformanceRating(killStats, memberCount)}")

        val guiItem = GuiItem(item) {
            openPerformanceDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addGraphPlaceholderButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack(Material.FILLED_MAP)
            .name("§5📊 Visual Charts")
            .lore("§7Interactive charts & graphs")
            .lore("§7Kill trends over time")
            .lore("§7Performance visualizations")
            .lore("")
            .lore("§eClick to view guild balance chart")
            .lore("§7Advanced map-based rendering")

        val guiItem = GuiItem(item) {
            renderGuildBalanceChart()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack(Material.ARROW)
            .name("§cBack to Control Panel")
            .lore("§7Return to guild management")

        val guiItem = GuiItem(item) {
            menuNavigator.openMenu(GuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    // Helper functions for calculations and ratings
    private fun calculateWinRate(wins: Int, totalWars: Int): String {
        return if (totalWars > 0) decimalFormat.format((wins.toDouble() / totalWars) * 100) else "0"
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
        player.sendMessage("§e🔪 Detailed kill statistics coming soon!")
    }

    private fun openWarStatsDetail() {
        player.sendMessage("§e⚔️ Detailed war statistics coming soon!")
    }

    private fun openMemberStatsDetail() {
        player.sendMessage("§e👥 Detailed member statistics coming soon!")
    }

    private fun openPerformanceDetail() {
        player.sendMessage("§e📊 Detailed performance analysis coming soon!")
    }

    private fun addTopKillersButton(pane: StaticPane, x: Int, y: Int) {
        val guildMembers = memberService.getGuildMembers(guild.id).map { it.playerId }
        val topKillers = killService.getTopKillers(guildMembers, 5)

        val item = ItemStack(Material.TOTEM_OF_UNDYING)
            .name("§cTop Killers")
            .lore("§7Guild's elite warriors")

        if (topKillers.isNotEmpty()) {
            item.lore("")
            topKillers.take(3).forEachIndexed { index, (playerId, stats) ->
                val playerName = Bukkit.getPlayer(playerId)?.name ?: "Unknown"
                item.lore("§${getRankColor(index + 1)}${index + 1}. $playerName: §f${stats.totalKills} kills")
            }
        } else {
            item.lore("§7No kill data available")
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
            .name("§6Top Contributors")
            .lore("§7Most generous members")

        if (topContributors.isNotEmpty()) {
            item.lore("")
            topContributors.forEachIndexed { index, contribution ->
                val playerName = contribution.playerName ?: "Unknown"
                item.lore("§${getRankColor(index + 1)}${index + 1}. $playerName: §f$${contribution.netContribution}")
            }
        } else {
            item.lore("§7No contribution data")
        }

        val guiItem = GuiItem(item) {
            openTopContributorsDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addKillDeathRatiosButton(pane: StaticPane, x: Int, y: Int) {
        val killStats = killService.getGuildKillStats(guild.id)

        val item = ItemStack(Material.COMPARATOR)
            .name("§dK/D Analysis")
            .lore("§7Kill/Death Ratio: §e${decimalFormat.format(killStats.killDeathRatio)}")
            .lore("§7Performance Grade: §${getKDRatingColor(killStats.killDeathRatio)}${getKDRating(killStats.killDeathRatio)}")
            .lore("")
            .lore("§7Efficiency Score: §f${calculateEfficiencyScore(killStats)}/100")

        val guiItem = GuiItem(item) {
            openKDAnalysisDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addRecentActivityButton(pane: StaticPane, x: Int, y: Int) {
        val recentKills = killService.getRecentGuildKills(guild.id, 10)
        val recentActivity = recentKills.size

        val item = ItemStack(Material.CLOCK)
            .name("§fRecent Activity")
            .lore("§7Last 24 hours")
            .lore("§7Kills: §f$recentActivity")
            .lore("§7Activity Level: §${getActivityColor(recentActivity)}${getActivityLevel(recentActivity)}")

        val guiItem = GuiItem(item) {
            openRecentActivityDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addPeriodStatsButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack(Material.BOOK)
            .name("§3Period Statistics")
            .lore("§7View stats by time period")
            .lore("§7Daily, Weekly, Monthly")
            .lore("§7Compare performance over time")

        val guiItem = GuiItem(item) {
            openPeriodStatsMenu()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addRivalryStatsButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack(Material.RED_BANNER)
            .name("§4Rivalry Statistics")
            .lore("§7Kills vs other guilds")
            .lore("§7Dominance rankings")
            .lore("§7Most aggressive rivals")

        val guiItem = GuiItem(item) {
            openRivalryStatsDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addActivityHeatmapButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack(Material.MAP)
            .name("§2Activity Heatmap")
            .lore("§7Visual activity patterns")
            .lore("§7Peak activity times")
            .lore("§7Geographic kill zones")

        val guiItem = GuiItem(item) {
            openActivityHeatmap()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addAchievementsButton(pane: StaticPane, x: Int, y: Int) {
        val killStats = killService.getGuildKillStats(guild.id)
        val achievementCount = calculateAchievementCount(killStats)

        val item = ItemStack(Material.TROPICAL_FISH_BUCKET)
            .name("§eGuild Achievements")
            .lore("§7Milestones unlocked: §f$achievementCount")
            .lore("§7Total Kills: §${getAchievementColor(killStats.totalKills)}${getKillMilestone(killStats.totalKills)}")
            .lore("§7Net Kills: §${getAchievementColor(killStats.netKills)}${getNetKillMilestone(killStats.netKills)}")

        val guiItem = GuiItem(item) {
            openAchievementsDetail()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addTrendAnalysisButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack(Material.REPEATER)
            .name("§3📈 Kill Trends")
            .lore("§7Kill performance over time")
            .lore("§7Track improvement patterns")
            .lore("§7Interactive trend chart")

        val guiItem = GuiItem(item) {
            renderKillTrendChart()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addComparisonButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack(Material.COMPARATOR)
            .name("§9📊 Member Contributions")
            .lore("§7Compare member contributions")
            .lore("§7Visual ranking chart")
            .lore("§7Identify top contributors")

        val guiItem = GuiItem(item) {
            renderMemberContributionsChart()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addExportStatsButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack(Material.WRITABLE_BOOK)
            .name("§fExport Statistics")
            .lore("§7Download detailed stats")
            .lore("§7CSV format for analysis")
            .lore("§7Secure file delivery")

        val guiItem = GuiItem(item) {
            exportGuildStatistics()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addRefreshStatsButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack(Material.KNOWLEDGE_BOOK)
            .name("§aRefresh Statistics")
            .lore("§7Update all statistics")
            .lore("§7Fetch latest data")

        val guiItem = GuiItem(item) {
            player.sendMessage("§a🔄 Refreshing statistics...")
            // Reopen the menu to refresh all data
            open()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addDetailedViewButton(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack(Material.SPYGLASS)
            .name("§bDetailed View")
            .lore("§7Advanced statistics")
            .lore("§7In-depth analysis")
            .lore("§7Raw data access")

        val guiItem = GuiItem(item) {
            openDetailedStatisticsView()
        }
        pane.addItem(guiItem, x, y)
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
        player.sendMessage("§e🏆 Detailed top killers rankings coming soon!")
    }

    private fun openTopContributorsDetail() {
        player.sendMessage("§e💰 Detailed contribution analysis coming soon!")
    }

    private fun openKDAnalysisDetail() {
        player.sendMessage("§e📈 Detailed K/D ratio analysis coming soon!")
    }

    private fun openRecentActivityDetail() {
        player.sendMessage("§e🕐 Detailed recent activity coming soon!")
    }

    private fun openPeriodStatsMenu() {
        player.sendMessage("§e📅 Period-based statistics coming soon!")
    }

    private fun openRivalryStatsDetail() {
        player.sendMessage("§e🏴 Rivalry statistics coming soon!")
    }

    private fun openActivityHeatmap() {
        player.sendMessage("§e🗺️ Activity heatmap coming soon!")
    }

    private fun openAchievementsDetail() {
        player.sendMessage("§e🏅 Achievement details coming soon!")
    }

    private fun openTrendAnalysis() {
        player.sendMessage("§e📈 Trend analysis coming soon!")
    }

    private fun openGuildComparison() {
        player.sendMessage("§e⚖️ Guild comparison coming soon!")
    }

    private fun openDetailedStatisticsView() {
        player.sendMessage("§e🔬 Detailed statistics view coming soon!")
    }

    private fun exportGuildStatistics() {
        player.sendMessage("§e📊 Statistics export feature coming soon!")
        player.sendMessage("§7Will generate CSV files with all guild data")
    }

    // Chart rendering methods
    private fun renderGuildBalanceChart() {
        try {
            player.sendMessage("§e📊 Generating guild balance trend chart...")

            // Create sample balance history data (in a real implementation, this would come from the database)
            val balanceHistory = listOf(
                "Jan" to 1000,
                "Feb" to 1200,
                "Mar" to 950,
                "Apr" to 1350,
                "May" to 1100,
                "Jun" to 1400,
                "Jul" to 1250
            )

            val chart = mapRendererService.renderCustomChart(
                title = "${guild.name} Balance Trend",
                dataPoints = balanceHistory,
                chartType = ChartType.LINE,
                player = player
            )

            if (chart != null) {
                player.inventory.addItem(chart)
                player.sendMessage("§a✅ Guild balance chart generated! Hold the map to view interactive trends.")
            } else {
                player.sendMessage("§c❌ Failed to generate balance chart. Please try again.")
            }

        } catch (e: Exception) {
            player.sendMessage("§c❌ Error generating balance chart: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun renderKillTrendChart() {
        try {
            player.sendMessage("§e📊 Generating kill trend analysis chart...")

            // Create sample kill trend data (in a real implementation, this would come from KillService)
            val killTrends = listOf(
                "Week 1" to 45,
                "Week 2" to 52,
                "Week 3" to 38,
                "Week 4" to 67,
                "Week 5" to 59,
                "Week 6" to 71,
                "Week 7" to 63
            )

            val chart = mapRendererService.renderCustomChart(
                title = "${guild.name} Kill Trends",
                dataPoints = killTrends,
                chartType = ChartType.LINE,
                player = player
            )

            if (chart != null) {
                player.inventory.addItem(chart)
                player.sendMessage("§a✅ Kill trend chart generated! Hold the map to view performance patterns.")
            } else {
                player.sendMessage("§c❌ Failed to generate kill trend chart. Please try again.")
            }

        } catch (e: Exception) {
            player.sendMessage("§c❌ Error generating kill trend chart: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun renderMemberContributionsChart() {
        try {
            player.sendMessage("§e📊 Generating member contributions chart...")

            // Create sample member contribution data (in a real implementation, this would come from BankService)
            val contributions = listOf(
                "Player1" to 2500,
                "Player2" to 1800,
                "Player3" to 3200,
                "Player4" to 950,
                "Player5" to 2100
            )

            val chart = mapRendererService.renderCustomChart(
                title = "${guild.name} Member Contributions",
                dataPoints = contributions,
                chartType = ChartType.BAR,
                player = player
            )

            if (chart != null) {
                player.inventory.addItem(chart)
                player.sendMessage("§a✅ Member contributions chart generated! Hold the map to view ranking visualization.")
            } else {
                player.sendMessage("§c❌ Failed to generate contributions chart. Please try again.")
            }

        } catch (e: Exception) {
            player.sendMessage("§c❌ Error generating contributions chart: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
