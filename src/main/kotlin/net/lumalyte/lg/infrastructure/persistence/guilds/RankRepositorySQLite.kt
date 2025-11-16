package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.SQLException
import java.util.UUID

class RankRepositorySQLite(private val storage: Storage<Database>) : RankRepository {
    
    private val ranks: MutableMap<UUID, Rank> = mutableMapOf()
    
    init {
        createRankTable()
        preload()
    }
    
    private fun createRankTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS ranks (
                id TEXT PRIMARY KEY,
                guild_id TEXT NOT NULL,
                name TEXT NOT NULL,
                priority INTEGER NOT NULL DEFAULT 0,
                permissions TEXT,
                icon TEXT,
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
            );
        """.trimIndent()
        
        try {
            storage.connection.executeUpdate(sql)
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create ranks table", e)
        }
    }
    
    private fun preload() {
        val sql = "SELECT * FROM ranks ORDER BY guild_id, priority"
        
        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val rank = mapResultSetToRank(result)
                ranks[rank.id] = rank
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload ranks", e)
        }
    }
    
    private fun mapResultSetToRank(rs: co.aikar.idb.DbRow): Rank {
        val id = UUID.fromString(rs.getString("id"))
        val guildId = UUID.fromString(rs.getString("guild_id"))
        val name = rs.getString("name")
        val priority = rs.getInt("priority")
        val permissionsStr = rs.getString("permissions")
        val icon = rs.getString("icon")

        val permissions = if (permissionsStr != null) {
            permissionsStr.split(",").filter { it.isNotBlank() }.map { RankPermission.valueOf(it.trim()) }.toSet()
        } else emptySet()

        return Rank(
            id = id,
            guildId = guildId,
            name = name,
            priority = priority,
            permissions = permissions,
            icon = icon
        )
    }
    
    override fun getAll(): Set<Rank> = ranks.values.toSet()
    
    override fun getById(id: UUID): Rank? = ranks[id]
    
    override fun getByGuild(guildId: UUID): Set<Rank> = ranks.values.filter { it.guildId == guildId }.toSet()
    
    override fun getByName(guildId: UUID, name: String): Rank? = 
        ranks.values.find { it.guildId == guildId && it.name.equals(name, ignoreCase = true) }
    
    override fun getDefaultRank(guildId: UUID): Rank? {
        return getByGuild(guildId).maxByOrNull { it.priority }
    }
    
    override fun getHighestRank(guildId: UUID): Rank? {
        return getByGuild(guildId).minByOrNull { it.priority }
    }
    
    override fun add(rank: Rank): Boolean {
        val sql = """
            INSERT INTO ranks (id, guild_id, name, priority, permissions, icon)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            val permissionsStr = rank.permissions.joinToString(",") { it.name }
            val rowsAffected = storage.connection.executeUpdate(sql,
                rank.id.toString(),
                rank.guildId.toString(),
                rank.name,
                rank.priority,
                permissionsStr,
                rank.icon
            )
            if (rowsAffected > 0) {
                ranks[rank.id] = rank
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }
    
    override fun update(rank: Rank): Boolean {
        val sql = """
            UPDATE ranks SET name = ?, priority = ?, permissions = ?, icon = ?
            WHERE id = ?
        """.trimIndent()

        return try {
            val permissionsStr = rank.permissions.joinToString(",") { it.name }
            val rowsAffected = storage.connection.executeUpdate(sql,
                rank.name,
                rank.priority,
                permissionsStr,
                rank.icon,
                rank.id.toString()
            )
            if (rowsAffected > 0) {
                ranks[rank.id] = rank
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }
    
    override fun remove(rankId: UUID): Boolean {
        val sql = "DELETE FROM ranks WHERE id = ?"
        
        return try {
            val rowsAffected = storage.connection.executeUpdate(sql, rankId.toString())
            if (rowsAffected > 0) {
                ranks.remove(rankId)
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }
    
    override fun removeByGuild(guildId: UUID): Boolean {
        val sql = "DELETE FROM ranks WHERE guild_id = ?"
        
        return try {
            val rowsAffected = storage.connection.executeUpdate(sql, guildId.toString())
            if (rowsAffected > 0) {
                ranks.entries.removeIf { it.value.guildId == guildId }
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }
    
    override fun isNameTaken(guildId: UUID, name: String): Boolean = getByName(guildId, name) != null
    
    override fun getNextPriority(guildId: UUID): Int {
        val existingRanks = getByGuild(guildId)
        return if (existingRanks.isEmpty()) 0 else existingRanks.maxOf { it.priority } + 1
    }
    
    override fun getCountByGuild(guildId: UUID): Int = getByGuild(guildId).size
}
