package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.DiplomacyService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.DiplomaticRelation
import net.lumalyte.lg.domain.entities.DiplomaticRelationType
import net.lumalyte.lg.domain.entities.Guild
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.max
import kotlin.math.min
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildDiplomaticStatusMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                                private val guild: Guild, private val messageService: MessageService) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val diplomacyService: DiplomacyService by inject()

    override fun open() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<aqua><aqua>Diplomatic Status - ${guild.name}"))
        val pane = StaticPane(0, 0, 9, 6)

        // Add overview section
        addDiplomaticOverview(pane)

        // Add relations breakdown
        addRelationsBreakdown(pane)

        // Add recent activity
        addRecentActivity(pane)

        // Add reputation metrics
        addReputationMetrics(pane)

        // Add navigation
        addNavigation(pane)

        gui.addPane(pane)
        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun addDiplomaticOverview(pane: StaticPane) {
        val relations = diplomacyService.getRelations(guild.id)

        // Calculate overview metrics
        val allies = relations.count { it.type == DiplomaticRelationType.ALLIANCE && it.isActive() }
        val enemies = relations.count { it.type == DiplomaticRelationType.ENEMY && it.isActive() }
        val truces = relations.count { it.type == DiplomaticRelationType.TRUCE && it.isActive() }
        val totalRelations = relations.size

        // Overall status
        val statusItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<yellow>Diplomatic Overview")
            .lore(listOf(
                "<gray>Total Relations: <white>$totalRelations",
                "<gray>Allies: <green>$allies",
                "<gray>Enemies: <red>$enemies",
                "<gray>Truces: <yellow>$truces",
                "<gray>Status: <white>${getDiplomaticStatus(totalRelations)}"
            ))

        pane.addItem(GuiItem(statusItem), 0, 0)

        // Guild power indicator
        val powerItem = ItemStack(getPowerMaterial(guild))
            .setAdventureName(player, messageService, "<gold>Guild Power Level")
            .lore(listOf(
                "<gray>Guild Level: <white>${guild.level}",
                "<gray>Member Count: <white>${guild.level * 5}", // Mock calculation
                "<gray>Power Rating: <white>${calculatePowerRating(guild)}",
                "<gray>Diplomatic Influence: <white>${calculateInfluence(totalRelations)}"
            ))

        pane.addItem(GuiItem(powerItem), 2, 0)

        // Activity score
        val activityScore = calculateActivityScore(relations)
        val activityItem = ItemStack(getActivityMaterial(activityScore))
            .setAdventureName(player, messageService, "<green>Diplomatic Activity")
            .lore(listOf(
                "<gray>Activity Score: <white>$activityScore/100",
                "<gray>Recent Actions: <white>${getRecentActionsCount()}",
                "<gray>Response Time: <white>${getAverageResponseTime()}",
                "<gray>Success Rate: <white>${calculateSuccessRate(relations)}%"
            ))

        pane.addItem(GuiItem(activityItem), 4, 0)

        // Reputation score
        val reputationScore = calculateReputationScore(relations)
        val reputationItem = ItemStack(getReputationMaterial(reputationScore))
            .setAdventureName(player, messageService, "<light_purple>Diplomatic Reputation")
            .lore(listOf(
                "<gray>Reputation Score: <white>$reputationScore/100",
                "<gray>Allied Guilds: <white>$allies",
                "<gray>Peaceful Relations: <white>${allies + truces}",
                "<gray>Trust Factor: <white>${calculateTrustFactor(relations)}"
            ))

        pane.addItem(GuiItem(reputationItem), 6, 0)

        // Diplomatic health
        val healthScore = calculateDiplomaticHealth(relations)
        val healthItem = ItemStack(getHealthMaterial(healthScore))
            .setAdventureName(player, messageService, "<red>Diplomatic Health")
            .lore(listOf(
                "<gray>Health Score: <white>$healthScore/100",
                "<gray>Active Conflicts: <white>$enemies",
                "<gray>Expired Relations: <white>${getExpiredRelationsCount(relations)}",
                "<gray>Stability: <white>${getStabilityRating(healthScore)}"
            ))

        pane.addItem(GuiItem(healthItem), 8, 0)
    }

    private fun addRelationsBreakdown(pane: StaticPane) {
        val relations = diplomacyService.getRelations(guild.id)
        val allies = relations.filter { it.type == DiplomaticRelationType.ALLIANCE && it.isActive() }
        val enemies = relations.filter { it.type == DiplomaticRelationType.ENEMY && it.isActive() }
        val truces = relations.filter { it.type == DiplomaticRelationType.TRUCE && it.isActive() }

        // Allies breakdown
        val alliesItem = ItemStack(Material.DIAMOND_SWORD)
            .setAdventureName(player, messageService, "<green>Allies Breakdown")
            .lore(listOf(
                "<gray>Total Allies: <white>${allies.size}",
                "<gray>Strong Alliances: <white>${allies.count { isStrongAlliance(it) }}",
                "<gray>Recent Alliances: <white>${allies.count { isRecentRelation(it) }}",
                "<gray>Average Duration: <white>${calculateAverageDuration(allies)} days"
            ))

        pane.addItem(GuiItem(alliesItem), 1, 2)

        // Enemies breakdown
        val enemiesItem = ItemStack(Material.IRON_SWORD)
            .setAdventureName(player, messageService, "<red>Enemies Breakdown")
            .lore(listOf(
                "<gray>Total Enemies: <white>${enemies.size}",
                "<gray>Active Wars: <white>${enemies.size}", // Assuming all enemies are in wars
                "<gray>Recent Conflicts: <white>${enemies.count { isRecentRelation(it) }}",
                "<gray>War Duration: <white>${calculateAverageDuration(enemies)} days"
            ))

        pane.addItem(GuiItem(enemiesItem), 3, 2)

        // Truces breakdown
        val trucesItem = ItemStack(Material.WHITE_BANNER)
            .setAdventureName(player, messageService, "<yellow>Truces Breakdown")
            .lore(listOf(
                "<gray>Active Truces: <white>${truces.size}",
                "<gray>Expiring Soon: <white>${truces.count { isExpiringSoon(it) }}",
                "<gray>Average Duration: <white>${calculateAverageDuration(truces)} days",
                "<gray>Recent Truces: <white>${truces.count { isRecentRelation(it) }}"
            ))

        pane.addItem(GuiItem(trucesItem), 5, 2)

        // Neutral guilds
        val neutralCount = getNeutralGuildsCount()
        val neutralItem = ItemStack(Material.BOOKSHELF)
            .setAdventureName(player, messageService, "<gray>Neutral Guilds")
            .lore(listOf(
                "<gray>Neutral Guilds: <white>$neutralCount",
                "<gray>Potential Allies: <white>${neutralCount / 2}", // Mock calculation
                "<gray>Potential Threats: <white>${neutralCount / 4}", // Mock calculation
                "<gray>Untapped Relations: <white>$neutralCount"
            ))

        pane.addItem(GuiItem(neutralItem), 7, 2)
    }

    private fun addRecentActivity(pane: StaticPane) {
        val recentActions = getRecentDiplomaticActions()

        // Recent activity summary
        val activityItem = ItemStack(Material.CLOCK)
            .setAdventureName(player, messageService, "<gold>Recent Diplomatic Activity")
            .lore(listOf(
                "<gray>Last 7 Days: <white>${recentActions.count { it.isRecent() }} actions",
                "<gray>Alliances Formed: <white>${recentActions.count { it.type == "alliance_formed" }}",
                "<gray>Truces Made: <white>${recentActions.count { it.type == "truce_made" }}",
                "<gray>Wars Declared: <white>${recentActions.count { it.type == "war_declared" }}",
                "<gray>Relations Broken: <white>${recentActions.count { it.type.contains("broken") }}"
            ))

        pane.addItem(GuiItem(activityItem), 0, 3)

        // Activity timeline (simplified)
        val timelineItem = ItemStack(Material.PAPER)
            .setAdventureName(player, messageService, "<green>Activity Timeline")
            .lore(listOf(
                "<gray>Most Recent: <white>${getMostRecentAction()}",
                "<gray>Response Rate: <white>${calculateResponseRate()}%",
                "<gray>Initiated Actions: <white>${getInitiatedActionsCountThisWeek()}",
                "<gray>Received Actions: <white>${getReceivedActionsCountThisWeek()}"
            ))

        pane.addItem(GuiItem(timelineItem), 2, 3)
    }

    private fun addReputationMetrics(pane: StaticPane) {
        val relations = diplomacyService.getRelations(guild.id)
        val reputation = calculateDetailedReputation(relations)

        // Trust score
        val trustItem = ItemStack(Material.EMERALD)
            .setAdventureName(player, messageService, "<green>Trust & Reliability")
            .lore(listOf(
                "<gray>Trust Score: <white>${reputation.trustScore}/100",
                "<gray>Kept Promises: <white>${reputation.promisesKept}",
                "<gray>Broken Agreements: <white>${reputation.agreementsBroken}",
                "<gray>Punctuality: <white>${reputation.punctualityScore}%"
            ))

        pane.addItem(GuiItem(trustItem), 4, 3)

        // Influence score
        val influenceItem = ItemStack(Material.GOLD_INGOT)
            .setAdventureName(player, messageService, "<gold>Diplomatic Influence")
            .lore(listOf(
                "<gray>Influence Score: <white>${reputation.influenceScore}/100",
                "<gray>Allies Influenced: <white>${reputation.alliesCount}",
                "<gray>Truces Mediated: <white>${reputation.trucesMediated}",
                "<gray>Peace Negotiations: <white>${reputation.peaceNegotiations}"
            ))

        pane.addItem(GuiItem(influenceItem), 6, 3)

        // Conflict resolution
        val conflictItem = ItemStack(Material.SHIELD)
            .setAdventureName(player, messageService, "<red>Conflict Resolution")
            .lore(listOf(
                "<gray>Wars Resolved: <white>${reputation.warsResolved}",
                "<gray>Peaceful Ends: <white>${reputation.peacefulResolutions}",
                "<gray>Surrenders: <white>${reputation.surrenders}",
                "<gray>Victory Rate: <white>${reputation.victoryRate}%"
            ))

        pane.addItem(GuiItem(conflictItem), 8, 3)
    }

    private fun addNavigation(pane: StaticPane) {
        // Back to Relations Hub
        val backItem = ItemStack(Material.BARRIER)
            .setAdventureName(player, messageService, "<red>Back to Relations")
            .lore(listOf("<gray>Return to relations menu"))

        pane.addItem(GuiItem(backItem) { _ ->
            menuNavigator.goBack()
        }, 4, 5)

        // Refresh data
        val refreshItem = ItemStack(Material.COMPASS)
            .setAdventureName(player, messageService, "<green>Refresh Data")
            .lore(listOf("<gray>Refresh diplomatic statistics"))

        pane.addItem(GuiItem(refreshItem) { _ ->
            open() // Reopen to refresh data
        }, 0, 5)

        // Export report (placeholder)
        val exportItem = ItemStack(Material.WRITABLE_BOOK)
            .setAdventureName(player, messageService, "<aqua>Export Report")
            .lore(listOf("<gray>Export diplomatic status report"))

        pane.addItem(GuiItem(exportItem) { _ ->
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Export feature coming soon!")
        }, 8, 5)
    }

    // Helper methods for calculations
    private fun getDiplomaticStatus(totalRelations: Int): String {
        return when {
            totalRelations == 0 -> "Isolated"
            totalRelations < 3 -> "Emerging"
            totalRelations < 7 -> "Established"
            totalRelations < 15 -> "Influential"
            else -> "Diplomatic Power"
        }
    }

    private fun calculatePowerRating(guild: Guild): Int {
        return min(100, guild.level * 10 + (guild.level * 5)) // Mock calculation
    }

    private fun calculateInfluence(totalRelations: Int): Int {
        return min(100, totalRelations * 8) // Mock calculation
    }

    private fun calculateActivityScore(relations: List<DiplomaticRelation>): Int {
        val baseScore = relations.size * 10
        val recentActivity = getRecentActionsCount() * 5
        return min(100, baseScore + recentActivity)
    }

    private fun calculateReputationScore(relations: List<DiplomaticRelation>): Int {
        val allies = relations.count { it.type == DiplomaticRelationType.ALLIANCE }
        val truces = relations.count { it.type == DiplomaticRelationType.TRUCE }
        return min(100, (allies * 15) + (truces * 10))
    }

    private fun calculateDiplomaticHealth(relations: List<DiplomaticRelation>): Int {
        val enemies = relations.count { it.type == DiplomaticRelationType.ENEMY }
        val expired = getExpiredRelationsCount(relations)
        val baseHealth = 100 - (enemies * 15) - (expired * 5)
        return max(0, baseHealth)
    }

    // Placeholder methods - would be implemented with actual data
    private fun getPowerMaterial(guild: Guild): Material = Material.DIAMOND_BLOCK
    private fun getActivityMaterial(score: Int): Material = Material.EMERALD_BLOCK
    private fun getReputationMaterial(score: Int): Material = Material.GOLD_BLOCK
    private fun getHealthMaterial(score: Int): Material = Material.REDSTONE_BLOCK

    private fun isStrongAlliance(relation: DiplomaticRelation): Boolean = true // Mock
    private fun isRecentRelation(relation: DiplomaticRelation): Boolean = true // Mock
    private fun calculateAverageDuration(relations: List<DiplomaticRelation>): Long = 7 // Mock
    private fun isExpiringSoon(relation: DiplomaticRelation): Boolean = false // Mock
    private fun getNeutralGuildsCount(): Int = 25 // Mock
    private fun getRecentActionsCount(): Int = 5 // Mock
    private fun getAverageResponseTime(): String = "2.3 hours" // Mock
    private fun calculateSuccessRate(relations: List<DiplomaticRelation>): Double = 85.5 // Mock
    private fun getExpiredRelationsCount(relations: List<DiplomaticRelation>): Int = 2 // Mock
    private fun getStabilityRating(score: Int): String = "Stable" // Mock
    private fun getRecentDiplomaticActions(): List<DiplomaticAction> = emptyList() // Mock
    private fun getMostRecentAction(): String = "Alliance formed with TestGuild" // Mock
    private fun calculateResponseRate(): Double = 92.3 // Mock
    private fun getInitiatedActionsCount(): Int = 12 // Mock
    private fun getReceivedActionsCount(): Int = 8 // Mock
    private fun calculateTrustFactor(relations: List<DiplomaticRelation>): Int = 87 // Mock
    private fun calculateDetailedReputation(relations: List<DiplomaticRelation>): ReputationData = ReputationData() // Mock
    private fun getInitiatedActionsCountThisWeek(): Int = 15 // Mock
    private fun getReceivedActionsCountThisWeek(): Int = 10 // Mock

    // Mock data classes
    private data class DiplomaticAction(
        val type: String,
        val timestamp: Instant,
        val description: String
    ) {
        fun isRecent(): Boolean = timestamp.isAfter(Instant.now().minusSeconds(604800)) // 7 days
    }

    private data class ReputationData(
        val trustScore: Int = 85,
        val promisesKept: Int = 23,
        val agreementsBroken: Int = 2,
        val punctualityScore: Int = 88,
        val influenceScore: Int = 76,
        val alliesCount: Int = 5,
        val trucesMediated: Int = 3,
        val peaceNegotiations: Int = 7,
        val warsResolved: Int = 4,
        val peacefulResolutions: Int = 3,
        val surrenders: Int = 1,
        val victoryRate: Double = 75.0
    )
}
