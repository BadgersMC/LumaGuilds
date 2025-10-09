package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.domain.entities.GuildHomes
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class GuildRepositorySQLite(private val storage: SQLiteStorage) : GuildRepository {
    
    private val guilds: MutableMap<UUID, Guild> = mutableMapOf()
    
    init {
        createGuildTable()
        preload()
    }
    
    private fun createGuildTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS guilds (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL UNIQUE,
                banner TEXT,
                emoji TEXT,
                tag TEXT,
                home_world TEXT,
                home_x INTEGER,
                home_y INTEGER,
                home_z INTEGER,
                level INTEGER NOT NULL DEFAULT 1,
                bank_balance INTEGER NOT NULL DEFAULT 0,
                mode TEXT NOT NULL DEFAULT 'Hostile',
                mode_changed_at TEXT,
                created_at TEXT NOT NULL
            );
        """.trimIndent()
        
        try {
            storage.connection.executeUpdate(sql)
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create guilds table", e)
        }
    }
    
    private fun preload() {
        val sql = "SELECT * FROM guilds"
        
        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val guild = mapResultSetToGuild(result)
                guilds[guild.id] = guild
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload guilds", e)
        }
    }
    
    private fun mapResultSetToGuild(rs: co.aikar.idb.DbRow): Guild {
        val id = UUID.fromString(rs.getString("id"))
        val name = rs.getString("name")
        val ownerId = UUID.fromString(rs.getString("owner_id"))
        val ownerName = rs.getString("owner_name")
        val banner = rs.getString("banner")
        val emoji = rs.getString("emoji")
        val tag = rs.getString("tag")
        val level = rs.getInt("level")
        val bankBalance = rs.getInt("bank_balance")
        val mode = GuildMode.valueOf(rs.getString("mode").uppercase())
        val modeChangedAt = rs.getString("mode_changed_at")?.let { Instant.parse(it) }
        val createdAt = Instant.parse(rs.getString("created_at"))

        val homes = if (rs.getString("home_world") != null) {
            val worldId = UUID.fromString(rs.getString("home_world"))
            val x = rs.getInt("home_x")
            val y = rs.getInt("home_y")
            val z = rs.getInt("home_z")
            val mainHome = GuildHome(worldId, net.lumalyte.lg.domain.values.Position3D(x, y, z))
            GuildHomes(mapOf("main" to mainHome))
        } else {
            GuildHomes.EMPTY
        }

        return Guild(
            id = id,
            name = name,
            ownerId = ownerId,
            ownerName = ownerName,
            banner = banner,
            emoji = emoji,
            tag = tag,
            homes = homes,
            level = level,
            bankBalance = bankBalance,
            mode = mode,
            modeChangedAt = modeChangedAt,
            createdAt = createdAt
        )
    }
    
    override fun getAll(): Set<Guild> = guilds.values.toSet()
    
    override fun getById(id: UUID): Guild? = guilds[id]
    
    override fun getByName(name: String): Guild? = guilds.values.find { it.name.equals(name, ignoreCase = true) }
    
    override fun getByPlayer(playerId: UUID): Set<Guild> {
        // TODO: Implement this
        // This will need to join with the members table
        // For now, return empty set - will be implemented when MemberRepository is available
        return emptySet()
    }
    
    override fun add(guild: Guild): Boolean {
        val sql = """
            INSERT INTO guilds (id, name, banner, emoji, tag, home_world, home_x, home_y, home_z, level, bank_balance, mode, mode_changed_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            // Extract main home for backward compatibility with existing schema
            val mainHome = guild.homes.defaultHome

            val rowsAffected = storage.connection.executeUpdate(sql,
                guild.id.toString(),
                guild.name,
                guild.banner,
                guild.emoji,
                guild.tag,
                mainHome?.worldId?.toString(),
                mainHome?.position?.x,
                mainHome?.position?.y,
                mainHome?.position?.z,
                guild.level,
                guild.bankBalance,
                guild.mode.name.lowercase(),
                guild.modeChangedAt?.toString(),
                guild.createdAt.toString()
            )
            guilds[guild.id] = guild
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }
    
    override fun update(guild: Guild): Boolean {
        val sql = """
            UPDATE guilds SET name = ?, banner = ?, emoji = ?, tag = ?, home_world = ?, home_x = ?, home_y = ?, home_z = ?,
            level = ?, bank_balance = ?, mode = ?, mode_changed_at = ?
            WHERE id = ?
        """.trimIndent()

        return try {
            // Extract main home for backward compatibility with existing schema
            val mainHome = guild.homes.defaultHome

            val rowsAffected = storage.connection.executeUpdate(sql,
                guild.name,
                guild.banner,
                guild.emoji,
                guild.tag,
                mainHome?.worldId?.toString(),
                mainHome?.position?.x,
                mainHome?.position?.y,
                mainHome?.position?.z,
                guild.level,
                guild.bankBalance,
                guild.mode.name.lowercase(),
                guild.modeChangedAt?.toString(),
                guild.id.toString()
            )
            if (rowsAffected > 0) {
                guilds[guild.id] = guild
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }
    
    override fun remove(guildId: UUID): Boolean {
        val sql = "DELETE FROM guilds WHERE id = ?"
        
        return try {
            val rowsAffected = storage.connection.executeUpdate(sql, guildId.toString())
            if (rowsAffected > 0) {
                guilds.remove(guildId)
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }
    
    override fun isNameTaken(name: String): Boolean = getByName(name) != null
    
    override fun getCount(): Int = guilds.size
}
