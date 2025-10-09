package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.*
import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.util.UUID

/**
 * Service interface for administrative moderation and surveillance capabilities
 * Provides comprehensive tools for monitoring and controlling guild activities
 */
interface AdminModerationService {

    // === ADMIN AUTHENTICATION ===
    /**
     * Verify if a player has admin privileges
     */
    fun hasAdminPermission(playerId: UUID, permission: AdminPermission): Boolean

    /**
     * Log admin action for audit trail
     */
    fun logAdminAction(adminId: UUID, action: AdminPermission, targetGuildId: UUID?, targetPlayerId: UUID?, description: String)

    // === GUILD SURVEILLANCE ===
    /**
     * Inspect guild chest contents
     */
    fun inspectGuildChest(guildId: UUID, adminId: UUID): Map<String, ItemStack>?

    /**
     * Remove items from guild chest
     */
    fun removeItemsFromGuildChest(guildId: UUID, items: Map<String, Int>, adminId: UUID, reason: String): Boolean

    /**
     * View guild financial records
     */
    fun viewGuildFinances(guildId: UUID, adminId: UUID): GuildFinancialReport

    /**
     * Modify guild bank balance (emergency override)
     */
    fun setGuildBalance(guildId: UUID, newBalance: Int, adminId: UUID, reason: String): Boolean

    // === PLAYER SURVEILLANCE ===
    /**
     * Access detailed player profile
     */
    fun getPlayerProfile(playerId: UUID, adminId: UUID): PlayerProfileData

    /**
     * Track player movement patterns
     */
    fun getPlayerMovementHistory(playerId: UUID, adminId: UUID, hours: Int = 24): List<PlayerLocationData>

    /**
     * Intercept private communications
     */
    fun interceptCommunications(partyId: UUID, adminId: UUID): List<CommunicationRecord>

    /**
     * Inspect player inventory
     */
    fun inspectPlayerInventory(playerId: UUID, adminId: UUID): Map<String, ItemStack>

    // === CONTENT MODERATION ===
    /**
     * Rename inappropriate guild names
     */
    fun renameInappropriateGuild(guildId: UUID, newName: String, adminId: UUID, reason: String): Boolean

    /**
     * Edit inappropriate guild descriptions
     */
    fun editInappropriateDescription(guildId: UUID, newDescription: String, adminId: UUID, reason: String): Boolean

    /**
     * Remove inappropriate banners
     */
    fun removeInappropriateBanner(guildId: UUID, adminId: UUID, reason: String): Boolean

    /**
     * Remove inappropriate tags
     */
    fun removeInappropriateTag(guildId: UUID, adminId: UUID, reason: String): Boolean

    // === SYSTEM OVERRIDES ===
    /**
     * Force join any guild (bypass restrictions)
     */
    fun forceJoinGuild(playerId: UUID, guildId: UUID, adminId: UUID, reason: String): Boolean

    /**
     * Override permission restrictions
     */
    fun bypassPermissionChecks(playerId: UUID, guildId: UUID, permission: RankPermission, adminId: UUID): Boolean

    /**
     * Force rank changes
     */
    fun forceRankChange(playerId: UUID, guildId: UUID, newRankId: UUID, adminId: UUID, reason: String): Boolean

    /**
     * Initiate emergency lockdown
     */
    fun initiateEmergencyLockdown(adminId: UUID, reason: String): Boolean

    // === SURVEILLANCE DASHBOARD ===
    /**
     * Get comprehensive surveillance data
     */
    fun getSurveillanceData(adminId: UUID): SurveillanceDashboard

    /**
     * Export surveillance data
     */
    fun exportSurveillanceData(adminId: UUID, format: ExportFormat): String

    // === AUTOMATED MONITORING ===
    /**
     * Get active surveillance alerts
     */
    fun getActiveAlerts(adminId: UUID): List<SurveillanceAlert>

    /**
     * Resolve surveillance alert
     */
    fun resolveAlert(alertId: UUID, adminId: UUID, resolution: String): Boolean

    // === AUDIT AND REPORTING ===
    /**
     * Get audit trail for specific period
     */
    fun getAuditTrail(adminId: UUID, startTime: Instant, endTime: Instant): List<AdminActionLog>

    /**
     * Generate compliance report
     */
    fun generateComplianceReport(adminId: UUID, guildId: UUID?, period: Int = 30): ComplianceReport
}

/**
 * Guild financial report data
 */
data class GuildFinancialReport(
    val guildId: UUID,
    val currentBalance: Int,
    val totalTransactions: Int,
    val suspiciousTransactions: List<BankTransaction>,
    val budgetUtilization: Map<BudgetCategory, Double>,
    val securityIncidents: Int,
    val lastAudit: Instant?
)

/**
 * Player profile data for surveillance
 */
data class PlayerProfileData(
    val playerId: UUID,
    val playerName: String,
    val currentGuilds: List<UUID>,
    val totalPlayTime: Long,
    val lastSeen: Instant,
    val suspiciousActivities: List<String>,
    val financialProfile: FinancialProfile,
    val socialProfile: SocialProfile
)

/**
 * Player financial profile
 */
data class FinancialProfile(
    val totalDeposits: Int,
    val totalWithdrawals: Int,
    val netContribution: Int,
    val suspiciousTransactions: List<BankTransaction>,
    val wealthIndicators: Map<String, Double>
)

/**
 * Player social profile
 */
data class SocialProfile(
    val partiesJoined: Int,
    val messagesSent: Int,
    val alliancesFormed: Int,
    val conflictsInvolved: Int,
    val reputationScore: Double
)

/**
 * Player location data for movement tracking
 */
data class PlayerLocationData(
    val timestamp: Instant,
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val guildActivity: Boolean
)

/**
 * Communication record for intercepted messages
 */
data class CommunicationRecord(
    val id: UUID,
    val senderId: UUID,
    val senderName: String,
    val partyId: UUID,
    val message: String,
    val timestamp: Instant,
    val isPrivate: Boolean,
    val suspiciousContent: Boolean
)

/**
 * Surveillance dashboard data
 */
data class SurveillanceDashboard(
    val totalGuilds: Int,
    val activeGuilds: Int,
    val suspiciousGuilds: Int,
    val totalPlayers: Int,
    val onlinePlayers: Int,
    val suspiciousPlayers: Int,
    val activeAlerts: List<SurveillanceAlert>,
    val recentAdminActions: List<AdminActionLog>,
    val systemHealth: SystemHealthStatus
)

/**
 * System health status
 */
data class SystemHealthStatus(
    val databaseConnection: Boolean,
    val surveillanceSystems: Boolean,
    val moderationTools: Boolean,
    val emergencyProtocols: Boolean
)

/**
 * Export format for surveillance data
 */
enum class ExportFormat {
    JSON,
    CSV,
    XML,
    PDF
}

/**
 * Compliance report
 */
data class ComplianceReport(
    val reportId: UUID,
    val generatedBy: UUID,
    val generatedAt: Instant,
    val period: Int,
    val guildId: UUID?,
    val totalActions: Int,
    val violations: List<ViolationRecord>,
    val recommendations: List<String>,
    val complianceScore: Double
)

/**
 * Violation record
 */
data class ViolationRecord(
    val type: ViolationType,
    val description: String,
    val severity: SurveillanceLevel,
    val timestamp: Instant,
    val resolved: Boolean
)

/**
 * Types of violations
 */
enum class ViolationType {
    INAPPROPRIATE_CONTENT,
    SUSPICIOUS_ACTIVITY,
    SECURITY_BREACH,
    POLICY_VIOLATION,
    UNAUTHORIZED_ACCESS
}
