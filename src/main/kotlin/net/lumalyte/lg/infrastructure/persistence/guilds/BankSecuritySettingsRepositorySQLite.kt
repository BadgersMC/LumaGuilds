package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.persistence.BankSecuritySettingsRepository
import net.lumalyte.lg.domain.entities.AuditAction
import net.lumalyte.lg.domain.entities.BankAudit
import net.lumalyte.lg.domain.entities.BankSecuritySettings
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import java.time.Instant
import java.util.UUID

/**
 * SQLite implementation of BankSecuritySettingsRepository.
 */
class BankSecuritySettingsRepositorySQLite(
    private val storage: SQLiteStorage
) : BankSecuritySettingsRepository {

    init {
        createTable()
    }

    private fun createTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS bank_security_settings (
                id TEXT PRIMARY KEY,
                guild_id TEXT NOT NULL UNIQUE,
                dual_auth_threshold INTEGER NOT NULL DEFAULT 1000,
                multi_signature_required BOOLEAN NOT NULL DEFAULT 0,
                multi_signature_count INTEGER NOT NULL DEFAULT 2,
                fraud_detection_enabled BOOLEAN NOT NULL DEFAULT 1,
                emergency_freeze BOOLEAN NOT NULL DEFAULT 0,
                audit_logging_enabled BOOLEAN NOT NULL DEFAULT 1,
                suspicious_activity_threshold INTEGER NOT NULL DEFAULT 5,
                auto_freeze_on_suspicious BOOLEAN NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
            );
        """.trimIndent()

        storage.connection.executeUpdate(sql)
    }

    override fun findByGuildId(guildId: UUID): BankSecuritySettings? {
        val sql = "SELECT * FROM bank_security_settings WHERE guild_id = ?"

        return try {
            val result = storage.connection.getFirstRow(sql, guildId.toString())
            result?.let { mapResultSetToEntity(it) }
        } catch (e: Exception) {
            null
        }
    }

    override fun save(settings: BankSecuritySettings): Boolean {
        val sql = """
            INSERT OR REPLACE INTO bank_security_settings (
                id, guild_id, dual_auth_threshold, multi_signature_required,
                multi_signature_count, fraud_detection_enabled, emergency_freeze,
                audit_logging_enabled, suspicious_activity_threshold,
                auto_freeze_on_suspicious, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return storage.connection.executeUpdate(sql,
            settings.id.toString(),
            settings.guildId.toString(),
            settings.dualAuthThreshold,
            if (settings.multiSignatureRequired) 1 else 0,
            settings.multiSignatureCount,
            if (settings.fraudDetectionEnabled) 1 else 0,
            if (settings.emergencyFreeze) 1 else 0,
            if (settings.auditLoggingEnabled) 1 else 0,
            settings.suspiciousActivityThreshold,
            if (settings.autoFreezeOnSuspiciousActivity) 1 else 0,
            settings.createdAt.toString(),
            settings.updatedAt.toString()
        ) > 0
    }

    override fun deleteByGuildId(guildId: UUID): Boolean {
        val sql = "DELETE FROM bank_security_settings WHERE guild_id = ?"
        return storage.connection.executeUpdate(sql, guildId.toString()) > 0
    }

    override fun findGuildsWithEmergencyFreeze(): List<UUID> {
        val sql = "SELECT guild_id FROM bank_security_settings WHERE emergency_freeze = 1"

        return try {
            val results = storage.connection.getResults(sql)
            results.map { UUID.fromString(it.getString("guild_id")) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun updateEmergencyFreeze(guildId: UUID, emergencyFreeze: Boolean): Boolean {
        val sql = "UPDATE bank_security_settings SET emergency_freeze = ?, updated_at = ? WHERE guild_id = ?"

        return storage.connection.executeUpdate(sql,
            if (emergencyFreeze) 1 else 0,
            Instant.now().toString(),
            guildId.toString()
        ) > 0
    }

    private fun mapResultSetToEntity(rs: co.aikar.idb.DbRow): BankSecuritySettings {
        return BankSecuritySettings(
            id = UUID.fromString(rs.getString("id")),
            guildId = UUID.fromString(rs.getString("guild_id")),
            dualAuthThreshold = rs.getInt("dual_auth_threshold"),
            multiSignatureRequired = rs.getInt("multi_signature_required") == 1,
            multiSignatureCount = rs.getInt("multi_signature_count"),
            fraudDetectionEnabled = rs.getInt("fraud_detection_enabled") == 1,
            emergencyFreeze = rs.getInt("emergency_freeze") == 1,
            auditLoggingEnabled = rs.getInt("audit_logging_enabled") == 1,
            suspiciousActivityThreshold = rs.getInt("suspicious_activity_threshold"),
            autoFreezeOnSuspiciousActivity = rs.getInt("auto_freeze_on_suspicious") == 1,
            createdAt = Instant.parse(rs.getString("created_at")),
            updatedAt = Instant.parse(rs.getString("updated_at"))
        )
    }
}
