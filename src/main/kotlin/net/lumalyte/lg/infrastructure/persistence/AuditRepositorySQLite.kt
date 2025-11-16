package net.lumalyte.lg.infrastructure.persistence

import co.aikar.idb.Database

import net.lumalyte.lg.application.persistence.AuditRepository
import net.lumalyte.lg.domain.entities.AuditRecord
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * SQLite implementation of AuditRepository.
 * 
 * This repository handles persistence of audit records to the SQLite database,
 * providing efficient storage and retrieval for audit trails.
 */
class AuditRepositorySQLite(
    private val storage: Storage<Database>
) : AuditRepository {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    init {
        createTable()
    }
    
    private fun createTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS audits (
                id TEXT PRIMARY KEY,
                time TEXT NOT NULL,
                actor_id TEXT NOT NULL,
                guild_id TEXT,
                action TEXT NOT NULL,
                details TEXT,
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE SET NULL
            )
        """.trimIndent()
        
        // Create indices for performance
        val guildIndexSql = "CREATE INDEX IF NOT EXISTS idx_audits_guild ON audits(guild_id)"
        val actorIndexSql = "CREATE INDEX IF NOT EXISTS idx_audits_actor ON audits(actor_id)"
        val actionIndexSql = "CREATE INDEX IF NOT EXISTS idx_audits_action ON audits(action)"
        val timeIndexSql = "CREATE INDEX IF NOT EXISTS idx_audits_time ON audits(time)"
        
        try {
            storage.connection.executeUpdate(sql)
            storage.connection.executeUpdate(guildIndexSql)
            storage.connection.executeUpdate(actorIndexSql)
            storage.connection.executeUpdate(actionIndexSql)
            storage.connection.executeUpdate(timeIndexSql)
        } catch (e: Exception) {
            logger.error("Failed to create audits table", e)
        }
    }
    
    override fun save(auditRecord: AuditRecord): Boolean {
        val sql = """
            INSERT INTO audits (id, time, actor_id, guild_id, action, details)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()
        
        return try {
            val rowsAffected = storage.connection.executeUpdate(
                sql,
                auditRecord.id.toString(),
                auditRecord.time.toString(),
                auditRecord.actorId.toString(),
                auditRecord.guildId?.toString(),
                auditRecord.action,
                auditRecord.details
            )
            rowsAffected > 0
        } catch (e: Exception) {
            logger.error("Failed to save audit record", e)
            false
        }
    }
    
    override fun getByGuild(guildId: UUID, limit: Int?): List<AuditRecord> {
        var sql = """
            SELECT * FROM audits 
            WHERE guild_id = ? 
            ORDER BY time DESC
        """.trimIndent()
        
        if (limit != null) {
            sql += " LIMIT ?"
        }
        
        return try {
            val params = mutableListOf<Any>(guildId.toString())
            if (limit != null) {
                params.add(limit)
            }
            
            val results = storage.connection.getResults(sql, *params.toTypedArray())
            results.mapNotNull { it.toAuditRecord() }
        } catch (e: Exception) {
            logger.error("Failed to get audit records for guild: $guildId", e)
            emptyList()
        }
    }
    
    override fun getByActor(actorId: UUID, limit: Int?): List<AuditRecord> {
        var sql = """
            SELECT * FROM audits 
            WHERE actor_id = ? 
            ORDER BY time DESC
        """.trimIndent()
        
        if (limit != null) {
            sql += " LIMIT ?"
        }
        
        return try {
            val params = mutableListOf<Any>(actorId.toString())
            if (limit != null) {
                params.add(limit)
            }
            
            val results = storage.connection.getResults(sql, *params.toTypedArray())
            results.mapNotNull { it.toAuditRecord() }
        } catch (e: Exception) {
            logger.error("Failed to get audit records for actor: $actorId", e)
            emptyList()
        }
    }
    
    override fun getByAction(action: String, limit: Int?): List<AuditRecord> {
        var sql = """
            SELECT * FROM audits 
            WHERE action = ? 
            ORDER BY time DESC
        """.trimIndent()
        
        if (limit != null) {
            sql += " LIMIT ?"
        }
        
        return try {
            val params = mutableListOf<Any>(action)
            if (limit != null) {
                params.add(limit)
            }
            
            val results = storage.connection.getResults(sql, *params.toTypedArray())
            results.mapNotNull { it.toAuditRecord() }
        } catch (e: Exception) {
            logger.error("Failed to get audit records for action: $action", e)
            emptyList()
        }
    }
    
    override fun getInTimeRange(startTime: Instant, endTime: Instant, limit: Int?): List<AuditRecord> {
        var sql = """
            SELECT * FROM audits 
            WHERE time >= ? AND time <= ? 
            ORDER BY time DESC
        """.trimIndent()
        
        if (limit != null) {
            sql += " LIMIT ?"
        }
        
        return try {
            val params = mutableListOf<Any>(startTime.toString(), endTime.toString())
            if (limit != null) {
                params.add(limit)
            }
            
            val results = storage.connection.getResults(sql, *params.toTypedArray())
            results.mapNotNull { it.toAuditRecord() }
        } catch (e: Exception) {
            logger.error("Failed to get audit records in time range: $startTime to $endTime", e)
            emptyList()
        }
    }
    
    override fun getById(id: UUID): AuditRecord? {
        val sql = "SELECT * FROM audits WHERE id = ?"
        
        return try {
            val results = storage.connection.getResults(sql, id.toString())
            if (results.isNotEmpty()) {
                results.first().toAuditRecord()
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to get audit record by ID: $id", e)
            null
        }
    }
    
    override fun getAll(limit: Int?): List<AuditRecord> {
        var sql = "SELECT * FROM audits ORDER BY time DESC"
        
        if (limit != null) {
            sql += " LIMIT ?"
        }
        
        return try {
            val params = if (limit != null) arrayOf(limit) else emptyArray()
            val results = storage.connection.getResults(sql, *params)
            results.mapNotNull { it.toAuditRecord() }
        } catch (e: Exception) {
            logger.error("Failed to get all audit records", e)
            emptyList()
        }
    }
    
    override fun deleteOlderThan(cutoffTime: Instant): Int {
        val sql = "DELETE FROM audits WHERE time < ?"
        
        return try {
            storage.connection.executeUpdate(sql, cutoffTime.toString())
        } catch (e: Exception) {
            logger.error("Failed to delete old audit records", e)
            0
        }
    }
    
    override fun getCountByGuild(guildId: UUID): Int {
        val sql = "SELECT COUNT(*) as count FROM audits WHERE guild_id = ?"
        
        return try {
            val result = storage.connection.getFirstRow(sql, guildId.toString())
            result?.getInt("count") ?: 0
        } catch (e: Exception) {
            logger.error("Failed to get audit count for guild: $guildId", e)
            0
        }
    }
    
    override fun getCountByActor(actorId: UUID): Int {
        val sql = "SELECT COUNT(*) as count FROM audits WHERE actor_id = ?"
        
        return try {
            val result = storage.connection.getFirstRow(sql, actorId.toString())
            result?.getInt("count") ?: 0
        } catch (e: Exception) {
            logger.error("Failed to get audit count for actor: $actorId", e)
            0
        }
    }
    
    override fun getCountByAction(action: String): Int {
        val sql = "SELECT COUNT(*) as count FROM audits WHERE action = ?"
        
        return try {
            val result = storage.connection.getFirstRow(sql, action)
            result?.getInt("count") ?: 0
        } catch (e: Exception) {
            logger.error("Failed to get audit count for action: $action", e)
            0
        }
    }
    
    private fun Map<String, Any>.toAuditRecord(): AuditRecord? {
        return try {
            val id = UUID.fromString(this["id"] as String)
            val time = Instant.parse(this["time"] as String)
            val actorId = UUID.fromString(this["actor_id"] as String)
            val guildId = (this["guild_id"] as String?)?.let { UUID.fromString(it) }
            val action = this["action"] as String
            val details = this["details"] as String?
            
            AuditRecord(
                id = id,
                time = time,
                actorId = actorId,
                guildId = guildId,
                action = action,
                details = details
            )
        } catch (e: Exception) {
            logger.error("Error converting result map to AuditRecord", e)
            null
        }
    }
}
