package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.domain.entities.GuildHomes
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.VaultStatus
import net.lumalyte.lg.domain.entities.GuildVaultLocation
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.persistence.getInstant
import net.lumalyte.lg.infrastructure.persistence.getInstantNotNull
import java.sql.SQLException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

class GuildRepositorySQLite(private val storage: Storage<Database>) : GuildRepository {

    private val guilds: MutableMap<UUID, Guild> = mutableMapOf()

    /**
     * Format Instant as SQL datetime string compatible with both SQLite and MariaDB.
     * MariaDB requires YYYY-MM-DD HH:MM:SS format, not ISO-8601.
     */
    private fun Instant.toSqlDateTime(): String {
        return DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC)
            .format(this)
    }

    /**
     * Cache the LFG column existence check result to avoid repeated PRAGMA queries.
     * This is set once during initialization and never changes during runtime.
     */
    private val hasLfgColumns: Boolean by lazy {
        checkColumnExists("guilds", "is_open") &&
        checkColumnExists("guilds", "join_fee_enabled") &&
        checkColumnExists("guilds", "join_fee_amount")
    }

    init {
        createGuildTable()
        preload()
    }

    /**
     * Database-agnostic column existence check.
     * Uses INFORMATION_SCHEMA for MariaDB or PRAGMA for SQLite.
     */
    private fun checkColumnExists(tableName: String, columnName: String): Boolean {
        return try {
            // Try MariaDB/MySQL INFORMATION_SCHEMA approach first
            try {
                val rows = storage.connection.getResults("""
                    SELECT COLUMN_NAME
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                    AND TABLE_NAME = ?
                    AND COLUMN_NAME = ?
                """.trimIndent(), tableName, columnName)
                return rows.isNotEmpty()
            } catch (e: Exception) {
                // Fall back to SQLite PRAGMA approach
                val rows = storage.connection.getResults("PRAGMA table_info($tableName)")
                rows.forEach { row ->
                    val name = row.getString("name")
                    if (name == columnName) {
                        return true
                    }
                }
                false
            }
        } catch (e: Exception) {
            println("WARN [GuildRepositorySQLite] Failed to check column existence: ${e.message}")
            false
        }
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
        val banner = rs.getString("banner")
        val emoji = rs.getString("emoji")
        val tag = rs.getString("tag")
        val level = rs.getInt("level")
        val bankBalance = rs.getInt("bank_balance")
        val mode = GuildMode.valueOf(rs.getString("mode").uppercase())
        val modeChangedAt = rs.getInstant("mode_changed_at")
        val createdAt = rs.getInstantNotNull("created_at")

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

        // Parse vault status
        val vaultStatusStr = rs.getString("vault_status")
        val vaultStatus = vaultStatusStr?.let {
            try {
                VaultStatus.valueOf(it.uppercase())
            } catch (e: IllegalArgumentException) {
                VaultStatus.NEVER_PLACED
            }
        } ?: VaultStatus.NEVER_PLACED

        // Parse vault chest location
        val vaultChestWorldStr = rs.getString("vault_chest_world")
        val vaultChestLocation = if (vaultChestWorldStr != null) {
            try {
                GuildVaultLocation(
                    worldId = UUID.fromString(vaultChestWorldStr),
                    x = rs.getInt("vault_chest_x"),
                    y = rs.getInt("vault_chest_y"),
                    z = rs.getInt("vault_chest_z")
                )
            } catch (e: SQLException) {
                null
            }
        } else {
            null
        }

        // Parse isOpen (default to false for existing guilds)
        val isOpen = try {
            rs.get<Int?>("is_open")?.let { it == 1 } ?: false
        } catch (e: Exception) {
            false
        }

        // Parse join fee settings (default to disabled for existing guilds)
        val joinFeeEnabled = try {
            rs.get<Int?>("join_fee_enabled")?.let { it == 1 } ?: false
        } catch (e: Exception) {
            false
        }

        val joinFeeAmount = try {
            rs.get<Int?>("join_fee_amount") ?: 0
        } catch (e: Exception) {
            0
        }

        // Debug logging for vault data loading
        println("DEBUG [GuildRepositorySQLite] Loading guild '$name'")
        println("  vault_status from DB: '$vaultStatusStr' -> $vaultStatus")
        println("  vault_chest_world from DB: '$vaultChestWorldStr'")
        println("  vault_chest_location: $vaultChestLocation")

        return Guild(
            id = id,
            name = name,
            banner = banner,
            emoji = emoji,
            tag = tag,
            homes = homes,
            level = level,
            bankBalance = bankBalance,
            mode = mode,
            modeChangedAt = modeChangedAt,
            createdAt = createdAt,
            vaultStatus = vaultStatus,
            vaultChestLocation = vaultChestLocation,
            isOpen = isOpen,
            joinFeeEnabled = joinFeeEnabled,
            joinFeeAmount = joinFeeAmount
        )
    }

    override fun getAll(): Set<Guild> = guilds.values.toSet()
    
    override fun getById(id: UUID): Guild? = guilds[id]
    
    override fun getByName(name: String): Guild? = guilds.values.find { it.name.equals(name, ignoreCase = true) }
    
    override fun getByPlayer(playerId: UUID): Set<Guild> {
        // Query guild IDs from members table, then get guilds from cache
        val sql = "SELECT guild_id FROM members WHERE player_id = ?"

        return try {
            val results = storage.connection.getResults(sql, playerId.toString())
            val guildSet = mutableSetOf<Guild>()
            for (result in results) {
                val guildId = UUID.fromString(result.getString("guild_id"))
                guilds[guildId]?.let { guildSet.add(it) }
            }
            guildSet
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to get guilds for player $playerId", e)
        }
    }
    
    override fun add(guild: Guild): Boolean {
        // Use cached column existence check
        val sql = if (hasLfgColumns) {
            """
            INSERT INTO guilds (id, name, banner, emoji, tag, home_world, home_x, home_y, home_z, level, bank_balance, mode, mode_changed_at, created_at, is_open, join_fee_enabled, join_fee_amount)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        } else {
            // Fallback for databases without LFG columns
            """
            INSERT INTO guilds (id, name, banner, emoji, tag, home_world, home_x, home_y, home_z, level, bank_balance, mode, mode_changed_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        }

        return try {
            // Extract main home for backward compatibility with existing schema
            val mainHome = guild.homes.defaultHome

            val rowsAffected = if (hasLfgColumns) {
                storage.connection.executeUpdate(sql,
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
                    guild.modeChangedAt?.toSqlDateTime(),
                    guild.createdAt.toSqlDateTime(),
                    if (guild.isOpen) 1 else 0,
                    if (guild.joinFeeEnabled) 1 else 0,
                    guild.joinFeeAmount
                )
            } else {
                // Fallback without LFG columns
                storage.connection.executeUpdate(sql,
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
                    guild.modeChangedAt?.toSqlDateTime(),
                    guild.createdAt.toSqlDateTime()
                )
            }
            guilds[guild.id] = guild
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }
    
    override fun update(guild: Guild): Boolean {
        // Use cached column existence check
        val sql = if (hasLfgColumns) {
            """
            UPDATE guilds SET name = ?, banner = ?, emoji = ?, tag = ?, home_world = ?, home_x = ?, home_y = ?, home_z = ?,
            level = ?, bank_balance = ?, mode = ?, mode_changed_at = ?,
            vault_status = ?, vault_chest_world = ?, vault_chest_x = ?, vault_chest_y = ?, vault_chest_z = ?, is_open = ?,
            join_fee_enabled = ?, join_fee_amount = ?
            WHERE id = ?
            """.trimIndent()
        } else {
            // Fallback for databases without LFG columns
            """
            UPDATE guilds SET name = ?, banner = ?, emoji = ?, tag = ?, home_world = ?, home_x = ?, home_y = ?, home_z = ?,
            level = ?, bank_balance = ?, mode = ?, mode_changed_at = ?,
            vault_status = ?, vault_chest_world = ?, vault_chest_x = ?, vault_chest_y = ?, vault_chest_z = ?
            WHERE id = ?
            """.trimIndent()
        }

        return try {
            // Extract main home for backward compatibility with existing schema
            val mainHome = guild.homes.defaultHome

            // Debug logging for vault updates
            println("DEBUG [GuildRepositorySQLite] Updating guild '${guild.name}' (${guild.id})")
            println("  vault_status: ${guild.vaultStatus.name}")
            println("  vault_chest_location: ${guild.vaultChestLocation}")

            val rowsAffected = if (hasLfgColumns) {
                storage.connection.executeUpdate(sql,
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
                    guild.modeChangedAt?.toSqlDateTime(),
                    guild.vaultStatus.name,
                    guild.vaultChestLocation?.worldId?.toString(),
                    guild.vaultChestLocation?.x,
                    guild.vaultChestLocation?.y,
                    guild.vaultChestLocation?.z,
                    if (guild.isOpen) 1 else 0,
                    if (guild.joinFeeEnabled) 1 else 0,
                    guild.joinFeeAmount,
                    guild.id.toString()
                )
            } else {
                // Fallback without LFG columns
                storage.connection.executeUpdate(sql,
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
                    guild.modeChangedAt?.toSqlDateTime(),
                    guild.vaultStatus.name,
                    guild.vaultChestLocation?.worldId?.toString(),
                    guild.vaultChestLocation?.x,
                    guild.vaultChestLocation?.y,
                    guild.vaultChestLocation?.z,
                    guild.id.toString()
                )
            }

            println("  rows affected: $rowsAffected")

            if (rowsAffected > 0) {
                guilds[guild.id] = guild
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            println("ERROR [GuildRepositorySQLite] Failed to update guild: ${e.message}")
            e.printStackTrace()
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
