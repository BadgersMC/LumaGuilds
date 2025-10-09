package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

/**
 * Guild bank security settings including dual authorization thresholds,
 * multi-signature requirements, and security configurations.
 */
data class BankSecuritySettings(
    val id: UUID,
    val guildId: UUID,
    val dualAuthThreshold: Int = 1000,
    val multiSignatureRequired: Boolean = false,
    val multiSignatureCount: Int = 2,
    val fraudDetectionEnabled: Boolean = true,
    val emergencyFreeze: Boolean = false,
    val auditLoggingEnabled: Boolean = true,
    val suspiciousActivityThreshold: Int = 5,
    val autoFreezeOnSuspiciousActivity: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    fun requiresDualAuth(amount: Int): Boolean {
        return amount >= dualAuthThreshold
    }

    fun requiresMultiSignature(amount: Int): Boolean {
        return multiSignatureRequired && amount >= dualAuthThreshold
    }

    fun isEmergencyFrozen(): Boolean {
        return emergencyFreeze
    }

    fun shouldFreezeOnSuspiciousActivity(suspiciousCount: Int): Boolean {
        return autoFreezeOnSuspiciousActivity && suspiciousCount >= suspiciousActivityThreshold
    }
}

/**
 * Bank audit entry for tracking security-related events and actions.
 */
data class BankAudit(
    val id: UUID,
    val guildId: UUID,
    val playerId: UUID,
    val action: AuditAction,
    val amount: Int? = null,
    val description: String? = null,
    val timestamp: Instant = Instant.now(),
    val ipAddress: String? = null,
    val location: String? = null,
    val suspicious: Boolean = false
) {
    companion object {
        fun createSuspiciousActivity(
            guildId: UUID,
            playerId: UUID,
            action: AuditAction,
            amount: Int? = null,
            description: String? = null,
            ipAddress: String? = null
        ): BankAudit {
            return BankAudit(
                id = UUID.randomUUID(),
                guildId = guildId,
                playerId = playerId,
                action = action,
                amount = amount,
                description = description,
                timestamp = Instant.now(),
                ipAddress = ipAddress,
                suspicious = true
            )
        }
    }
}

/**
 * Actions that can be audited in the bank system.
 */
enum class AuditAction {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER,
    BALANCE_CHECK,
    PERMISSION_DENIED,
    INSUFFICIENT_FUNDS,
    FEE_CHARGED,
    SECURITY_SETTING_CHANGE,
    EMERGENCY_FREEZE_ACTIVATED,
    EMERGENCY_FREEZE_DEACTIVATED,
    SUSPICIOUS_ACTIVITY_DETECTED,
    FRAUD_ALERT,
    MULTI_SIGNATURE_APPROVAL,
    MULTI_SIGNATURE_REJECTION
}
