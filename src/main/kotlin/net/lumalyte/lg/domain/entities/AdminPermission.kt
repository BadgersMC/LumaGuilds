package net.lumalyte.lg.domain.entities

/**
 * Administrative permissions for guild moderation and surveillance
 * Designed for comprehensive monitoring and control capabilities
 */
enum class AdminPermission(
    val description: String,
    val surveillanceLevel: SurveillanceLevel
) {
    // === BASIC ADMINISTRATION ===
    ACCESS_ADMIN_PANEL("Access administrative control panel", SurveillanceLevel.LOW),

    // === GUILD SURVEILLANCE ===
    VIEW_GUILD_CHESTS("Inspect and view contents of guild chests", SurveillanceLevel.HIGH),
    MODIFY_GUILD_CHESTS("Remove items from guild chests", SurveillanceLevel.CRITICAL),
    VIEW_GUILD_FINANCES("Monitor guild bank transactions and balances", SurveillanceLevel.HIGH),
    MODIFY_GUILD_BALANCES("Set guild bank balances (emergency override)", SurveillanceLevel.CRITICAL),

    // === PLAYER SURVEILLANCE ===
    VIEW_PLAYER_PROFILES("Access detailed player profiles and activity", SurveillanceLevel.MEDIUM),
    TRACK_PLAYER_MOVEMENT("Monitor player location and movement patterns", SurveillanceLevel.HIGH),
    INTERCEPT_COMMUNICATIONS("View private party chats and messages", SurveillanceLevel.CRITICAL),
    ACCESS_PLAYER_INVENTORY("Inspect player inventories and equipment", SurveillanceLevel.HIGH),

    // === CONTENT MODERATION ===
    MODERATE_GUILD_NAMES("Rename inappropriate guild names", SurveillanceLevel.HIGH),
    MODERATE_GUILD_DESCRIPTIONS("Edit inappropriate guild descriptions", SurveillanceLevel.HIGH),
    MODERATE_BANNERS("Remove inappropriate guild banners", SurveillanceLevel.MEDIUM),
    MODERATE_TAGS("Remove inappropriate guild tags", SurveillanceLevel.MEDIUM),

    // === SYSTEM OVERRIDES ===
    BYPASS_GUILD_RESTRICTIONS("Join any guild regardless of restrictions", SurveillanceLevel.CRITICAL),
    BYPASS_PERMISSION_CHECKS("Override all permission restrictions", SurveillanceLevel.CRITICAL),
    FORCE_RANK_CHANGES("Force rank changes without permission checks", SurveillanceLevel.CRITICAL),
    EMERGENCY_LOCKDOWN("Initiate emergency server lockdown", SurveillanceLevel.CRITICAL),

    // === SURVEILLANCE TOOLS ===
    ACCESS_SURVEILLANCE_DASHBOARD("View comprehensive surveillance dashboard", SurveillanceLevel.HIGH),
    EXPORT_SURVEILLANCE_DATA("Export surveillance data for analysis", SurveillanceLevel.MEDIUM),
    REAL_TIME_MONITORING("Real-time monitoring of guild activities", SurveillanceLevel.HIGH),
    ANOMALY_DETECTION("Automated detection of suspicious activities", SurveillanceLevel.HIGH),

    // === AUDIT AND LOGGING ===
    VIEW_AUDIT_TRAILS("Access complete audit logs of all actions", SurveillanceLevel.MEDIUM),
    VIEW_MODERATION_HISTORY("Review all moderation actions taken", SurveillanceLevel.MEDIUM),
    VIEW_SECURITY_BREACHES("Monitor and respond to security incidents", SurveillanceLevel.HIGH),
    GENERATE_COMPLIANCE_REPORTS("Generate compliance and audit reports", SurveillanceLevel.MEDIUM)
}

/**
 * Surveillance levels indicating the sensitivity of admin actions
 */
enum class SurveillanceLevel {
    LOW,      // Basic administrative functions
    MEDIUM,   // Moderate surveillance capabilities
    HIGH,     // Advanced monitoring and control
    CRITICAL  // Maximum surveillance and override capabilities
}

/**
 * Admin action log for tracking all administrative activities
 */
data class AdminActionLog(
    val id: java.util.UUID,
    val adminId: java.util.UUID,
    val action: AdminPermission,
    val targetGuildId: java.util.UUID?,
    val targetPlayerId: java.util.UUID?,
    val description: String,
    val timestamp: java.time.Instant,
    val ipAddress: String?,
    val sessionId: String?,
    val severity: SurveillanceLevel,
    val metadata: Map<String, Any> = emptyMap()
) {
    companion object {
        fun createSuspiciousActivity(
            adminId: java.util.UUID,
            action: AdminPermission,
            description: String,
            metadata: Map<String, Any> = emptyMap()
        ): AdminActionLog {
            return AdminActionLog(
                id = java.util.UUID.randomUUID(),
                adminId = adminId,
                action = action,
                targetGuildId = null,
                targetPlayerId = null,
                description = "SUSPICIOUS: $description",
                timestamp = java.time.Instant.now(),
                ipAddress = null,
                sessionId = null,
                severity = SurveillanceLevel.CRITICAL,
                metadata = metadata
            )
        }
    }
}

/**
 * Surveillance alert for automated monitoring
 */
data class SurveillanceAlert(
    val id: java.util.UUID,
    val alertType: AlertType,
    val severity: SurveillanceLevel,
    val description: String,
    val detectedAt: java.time.Instant,
    val guildId: java.util.UUID?,
    val playerId: java.util.UUID?,
    val metadata: Map<String, Any> = emptyMap(),
    val resolved: Boolean = false,
    val resolvedBy: java.util.UUID? = null,
    val resolvedAt: java.time.Instant? = null
)

/**
 * Types of surveillance alerts
 */
enum class AlertType {
    SUSPICIOUS_ACTIVITY,
    UNAUTHORIZED_ACCESS,
    FINANCIAL_ANOMALY,
    COMMUNICATION_VIOLATION,
    CONTENT_VIOLATION,
    SECURITY_BREACH,
    SYSTEM_ANOMALY
}

/**
 * Admin session for tracking admin activities
 */
data class AdminSession(
    val id: java.util.UUID,
    val adminId: java.util.UUID,
    val startTime: java.time.Instant,
    val endTime: java.time.Instant? = null,
    val ipAddress: String?,
    val userAgent: String?,
    val actions: MutableList<AdminActionLog> = mutableListOf()
) {
    fun addAction(action: AdminActionLog) {
        actions.add(action)
    }

    fun endSession() {
        // Create final audit entry
        AdminActionLog.createSuspiciousActivity(
            adminId = adminId,
            action = AdminPermission.ACCESS_ADMIN_PANEL,
            description = "Admin session ended",
            metadata = mapOf("sessionDuration" to java.time.Duration.between(startTime, java.time.Instant.now()).toMinutes())
        )
    }
}
