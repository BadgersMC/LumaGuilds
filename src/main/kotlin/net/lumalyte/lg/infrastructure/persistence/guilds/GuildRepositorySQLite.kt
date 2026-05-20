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
        checkColumnExists("guilds", "join_fee_amount") &&
        checkColumnExists("guilds", "bannerman_enabled")
    }

    private val hasTrackingColumn: Boolean by lazy {
        checkColumnExists("guilds", "tracking_enabled")
    }

    private val hasBankFrozenColumn: Boolean by lazy {
        checkColumnExists("guilds", "bank_frozen")
    }

    init {
        createGuildTable()
        createGuildHomesTable()
        migrateTrackingColumn()
        migrateBankFrozenColumn()
        migrateAllyHomeColumns()
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
    
    /**
     * Creates the `guild_homes` table if it doesn't exist. This is the primary store
     * for guild homes (introduced in schema v19); the legacy `guilds.home_*` columns
     * remain in place as a single-home fallback for one release.
     */
    private fun createGuildHomesTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS guild_homes (
                guild_id TEXT NOT NULL,
                name TEXT NOT NULL,
                world_id TEXT NOT NULL,
                x INTEGER NOT NULL,
                y INTEGER NOT NULL,
                z INTEGER NOT NULL,
                allowed_ranks TEXT,
                PRIMARY KEY (guild_id, name),
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
            );
        """.trimIndent()

        try {
            storage.connection.executeUpdate(sql)
            storage.connection.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_guild_homes_guild_id ON guild_homes(guild_id)"
            )
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create guild_homes table", e)
        }
    }

    private fun migrateTrackingColumn() {
        // Always attempt ALTER TABLE; catch "duplicate column" silently so re-runs are safe.
        try {
            storage.connection.executeUpdate(
                "ALTER TABLE guilds ADD COLUMN tracking_enabled INTEGER NOT NULL DEFAULT 1"
            )
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (!msg.contains("duplicate column", ignoreCase = true) &&
                !msg.contains("already exists", ignoreCase = true)) {
                println("WARN [GuildRepositorySQLite] Failed to add tracking_enabled column: $msg")
            }
        }
    }

    private fun migrateBankFrozenColumn() {
        // Always attempt ALTER TABLE; catch "duplicate column" silently so re-runs are safe.
        try {
            storage.connection.executeUpdate(
                "ALTER TABLE guilds ADD COLUMN bank_frozen INTEGER NOT NULL DEFAULT 0"
            )
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (!msg.contains("duplicate column", ignoreCase = true) &&
                !msg.contains("already exists", ignoreCase = true)) {
                println("WARN [GuildRepositorySQLite] Failed to add bank_frozen column: $msg")
            }
        }
    }

    private fun migrateAllyHomeColumns() {
        val columns = listOf(
            "ally_home_world TEXT",
            "ally_home_x INTEGER",
            "ally_home_y INTEGER",
            "ally_home_z INTEGER",
            "ally_home_allowed_guilds TEXT"
        )
        for (colDef in columns) {
            try {
                storage.connection.executeUpdate("ALTER TABLE guilds ADD COLUMN $colDef")
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                if (!msg.contains("duplicate column", ignoreCase = true) &&
                    !msg.contains("already exists", ignoreCase = true)) {
                    println("WARN [GuildRepositorySQLite] Failed to add ally home column: $msg")
                }
            }
        }
    }

    private fun preload() {
        val sql = "SELECT * FROM guilds"

        try {
            // Pre-load every guild's homes from guild_homes in a single pass to avoid
            // N+1 queries during startup.
            val homesByGuild = loadAllGuildHomes()

            val results = storage.connection.getResults(sql)
            for (result in results) {
                val guild = mapResultSetToGuild(result, homesByGuild)
                guilds[guild.id] = guild
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload guilds", e)
        }
    }

    /**
     * Loads every row from `guild_homes` and groups them by guild id. Returns an empty
     * map if the table doesn't exist (e.g. someone is running this build against an
     * un-migrated DB) — `mapResultSetToGuild` will fall back to the legacy columns.
     */
    private fun loadAllGuildHomes(): Map<UUID, GuildHomes> {
        val byGuild = mutableMapOf<UUID, MutableMap<String, GuildHome>>()
        try {
            val rows = storage.connection.getResults(
                "SELECT guild_id, name, world_id, x, y, z, allowed_ranks FROM guild_homes"
            )
            for (row in rows) {
                val guildId = UUID.fromString(row.getString("guild_id"))
                val homeName = row.getString("name")
                val worldId = UUID.fromString(row.getString("world_id"))
                val x = row.getInt("x")
                val y = row.getInt("y")
                val z = row.getInt("z")
                val allowedCsv = row.getString("allowed_ranks").orEmpty()
                val allowedRankIds = allowedCsv.split(",")
                    .mapNotNull { token ->
                        val trimmed = token.trim()
                        if (trimmed.isEmpty()) null else try {
                            UUID.fromString(trimmed)
                        } catch (e: IllegalArgumentException) {
                            println("WARN [GuildRepositorySQLite] Ignoring invalid allowed_ranks UUID '$trimmed' for guild '$guildId' home '$homeName': ${e.message}")
                            null
                        }
                    }
                    .toSet()
                val home = GuildHome(worldId, net.lumalyte.lg.domain.values.Position3D(x, y, z), allowedRankIds)
                byGuild.getOrPut(guildId) { mutableMapOf() }[homeName] = home
            }
        } catch (e: SQLException) {
            // Table may not exist on a freshly upgraded plugin running against a stale DB.
            // Fall back to legacy columns by returning an empty map.
            println("WARN [GuildRepositorySQLite] guild_homes preload failed (continuing with legacy columns): ${e.message}")
            return emptyMap()
        }
        return byGuild.mapValues { (_, m) -> GuildHomes(m.toMap()) }
    }

    /**
     * Replaces all rows for the given guild in `guild_homes` with the entries from
     * the in-memory model. Called from `add` and `update`.
     *
     * The DELETE + re-INSERT pair runs inside a single SQLite transaction so that a
     * mid-loop INSERT failure cannot leave the table empty (which would silently
     * wipe every named home — the legacy `guilds.home_*` columns only cover the
     * default home). On any failure the transaction is rolled back and the caller
     * sees a thrown SQLException, which the outer `add`/`update` catch translates
     * to a `false` return so the in-memory cache stays in sync with the DB.
     */
    private fun writeGuildHomes(guild: Guild) {
        val deleteSql = "DELETE FROM guild_homes WHERE guild_id = ?"
        val insertSql = """
            INSERT INTO guild_homes (guild_id, name, world_id, x, y, z, allowed_ranks)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        val committed = storage.connection.createTransaction { stmt ->
            stmt.executeUpdateQuery(deleteSql, guild.id.toString())
            for ((homeName, home) in guild.homes.homes) {
                stmt.executeUpdateQuery(
                    insertSql,
                    guild.id.toString(),
                    homeName,
                    home.worldId.toString(),
                    home.position.x,
                    home.position.y,
                    home.position.z,
                    home.allowedRankIds.joinToString(",") { it.toString() }
                )
            }
            true
        }
        if (!committed) {
            throw SQLException("guild_homes write transaction did not commit for ${guild.id}")
        }
    }

    private fun mapResultSetToGuild(
        rs: co.aikar.idb.DbRow,
        homesByGuild: Map<UUID, GuildHomes> = emptyMap()
    ): Guild {
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

        // Prefer rows from the new `guild_homes` table (supports multiple named homes).
        // Fall back to the legacy `guilds.home_*` columns when no rows exist for this guild,
        // mapping the legacy single home to the name "main" for backward compatibility.
        val preloadedHomes = homesByGuild[id]
        val homes = when {
            preloadedHomes != null && preloadedHomes.hasHomes() -> preloadedHomes
            rs.getString("home_world") != null -> {
                val worldId = UUID.fromString(rs.getString("home_world"))
                val x = rs.getInt("home_x")
                val y = rs.getInt("home_y")
                val z = rs.getInt("home_z")
                val mainHome = GuildHome(worldId, net.lumalyte.lg.domain.values.Position3D(x, y, z))
                GuildHomes(mapOf("main" to mainHome))
            }
            else -> GuildHomes.EMPTY
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

        val bannermanEnabled = try {
            rs.get<Int?>("bannerman_enabled")?.let { it == 1 } ?: false
        } catch (e: Exception) {
            false
        }

        // Parse tracking_enabled (default to true for existing guilds)
        val trackingEnabled = try {
            rs.get<Int?>("tracking_enabled")?.let { it == 1 } ?: true
        } catch (e: Exception) {
            true
        }

        // Parse bank_frozen (default to false for existing guilds)
        val bankFrozen = try {
            rs.get<Int?>("bank_frozen")?.let { it == 1 } ?: false
        } catch (e: Exception) {
            false
        }

        // Parse ally home (explicitly set home for allied guilds to teleport to)
        val allyHome = try {
            val allyWorldStr = rs.getString("ally_home_world")
            if (allyWorldStr != null) {
                GuildHome(
                    worldId = UUID.fromString(allyWorldStr),
                    position = net.lumalyte.lg.domain.values.Position3D(
                        rs.getInt("ally_home_x"),
                        rs.getInt("ally_home_y"),
                        rs.getInt("ally_home_z")
                    )
                )
            } else null
        } catch (e: Exception) {
            null
        }

        val allyHomeAllowedGuilds: Set<UUID> = try {
            val csv = rs.getString("ally_home_allowed_guilds").orEmpty()
            csv.split(",")
                .mapNotNull { token ->
                    val trimmed = token.trim()
                    if (trimmed.isEmpty()) null else try {
                        UUID.fromString(trimmed)
                    } catch (ex: IllegalArgumentException) {
                        println("WARN [GuildRepositorySQLite] Ignoring invalid ally_home_allowed_guilds UUID '$trimmed' for guild '$id': ${ex.message}")
                        null
                    }
                }
                .toSet()
        } catch (e: SQLException) {
            // Column may not exist yet on a freshly upgraded plugin running against a stale DB.
            println("WARN [GuildRepositorySQLite] Failed to load ally_home_allowed_guilds for guild '$id': ${e.message}")
            emptySet()
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
            joinFeeAmount = joinFeeAmount,
            trackingEnabled = trackingEnabled,
            bankFrozen = bankFrozen,
            bannermanEnabled = bannermanEnabled,
            allyHome = allyHome,
            allyHomeAllowedGuilds = allyHomeAllowedGuilds
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
        val sql = if (hasLfgColumns && hasTrackingColumn && hasBankFrozenColumn) {
            """
            INSERT INTO guilds (id, name, banner, emoji, tag, home_world, home_x, home_y, home_z, level, bank_balance, mode, mode_changed_at, created_at, is_open, join_fee_enabled, join_fee_amount, tracking_enabled, bank_frozen, bannerman_enabled, ally_home_world, ally_home_x, ally_home_y, ally_home_z, ally_home_allowed_guilds)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        } else if (hasLfgColumns && hasTrackingColumn) {
            """
            INSERT INTO guilds (id, name, banner, emoji, tag, home_world, home_x, home_y, home_z, level, bank_balance, mode, mode_changed_at, created_at, is_open, join_fee_enabled, join_fee_amount, tracking_enabled, bannerman_enabled, ally_home_world, ally_home_x, ally_home_y, ally_home_z, ally_home_allowed_guilds)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        } else if (hasLfgColumns) {
            """
            INSERT INTO guilds (id, name, banner, emoji, tag, home_world, home_x, home_y, home_z, level, bank_balance, mode, mode_changed_at, created_at, is_open, join_fee_enabled, join_fee_amount, bannerman_enabled, ally_home_world, ally_home_x, ally_home_y, ally_home_z, ally_home_allowed_guilds)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        } else if (hasTrackingColumn) {
            """
            INSERT INTO guilds (id, name, banner, emoji, tag, home_world, home_x, home_y, home_z, level, bank_balance, mode, mode_changed_at, created_at, tracking_enabled, ally_home_world, ally_home_x, ally_home_y, ally_home_z, ally_home_allowed_guilds)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        } else {
            """
            INSERT INTO guilds (id, name, banner, emoji, tag, home_world, home_x, home_y, home_z, level, bank_balance, mode, mode_changed_at, created_at, ally_home_world, ally_home_x, ally_home_y, ally_home_z, ally_home_allowed_guilds)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        }

        return try {
            // Extract main home for backward compatibility with existing schema
            val mainHome = guild.homes.defaultHome

            val rowsAffected = if (hasLfgColumns && hasTrackingColumn && hasBankFrozenColumn) {
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
                    guild.joinFeeAmount,
                    if (guild.trackingEnabled) 1 else 0,
                    if (guild.bankFrozen) 1 else 0,
                    if (guild.bannermanEnabled) 1 else 0,
                    guild.allyHome?.worldId?.toString(),
                    guild.allyHome?.position?.x,
                    guild.allyHome?.position?.y,
                    guild.allyHome?.position?.z,
                    guild.allyHomeAllowedGuilds.joinToString(",") { it.toString() }
                )
            } else if (hasLfgColumns && hasTrackingColumn) {
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
                    guild.joinFeeAmount,
                    if (guild.trackingEnabled) 1 else 0,
                    if (guild.bannermanEnabled) 1 else 0,
                    guild.allyHome?.worldId?.toString(),
                    guild.allyHome?.position?.x,
                    guild.allyHome?.position?.y,
                    guild.allyHome?.position?.z,
                    guild.allyHomeAllowedGuilds.joinToString(",") { it.toString() }
                )
            } else if (hasLfgColumns) {
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
                    guild.joinFeeAmount,
                    if (guild.bannermanEnabled) 1 else 0,
                    guild.allyHome?.worldId?.toString(),
                    guild.allyHome?.position?.x,
                    guild.allyHome?.position?.y,
                    guild.allyHome?.position?.z,
                    guild.allyHomeAllowedGuilds.joinToString(",") { it.toString() }
                )
            } else if (hasTrackingColumn) {
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
                    if (guild.trackingEnabled) 1 else 0,
                    guild.allyHome?.worldId?.toString(),
                    guild.allyHome?.position?.x,
                    guild.allyHome?.position?.y,
                    guild.allyHome?.position?.z,
                    guild.allyHomeAllowedGuilds.joinToString(",") { it.toString() }
                )
            } else {
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
                    guild.allyHome?.worldId?.toString(),
                    guild.allyHome?.position?.x,
                    guild.allyHome?.position?.y,
                    guild.allyHome?.position?.z,
                    guild.allyHomeAllowedGuilds.joinToString(",") { it.toString() }
                )
            }
            if (rowsAffected > 0) {
                // Persist the full named-home map; legacy home_* columns above only cover the default home.
                writeGuildHomes(guild)
                guilds[guild.id] = guild
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }

    override fun update(guild: Guild): Boolean {
        // Use cached column existence check
        val sql = if (hasLfgColumns && hasTrackingColumn && hasBankFrozenColumn) {
            """
            UPDATE guilds SET name = ?, banner = ?, emoji = ?, tag = ?, home_world = ?, home_x = ?, home_y = ?, home_z = ?,
            level = ?, bank_balance = ?, mode = ?, mode_changed_at = ?,
            vault_status = ?, vault_chest_world = ?, vault_chest_x = ?, vault_chest_y = ?, vault_chest_z = ?, is_open = ?,
            join_fee_enabled = ?, join_fee_amount = ?, tracking_enabled = ?, bank_frozen = ?, bannerman_enabled = ?,
            ally_home_world = ?, ally_home_x = ?, ally_home_y = ?, ally_home_z = ?, ally_home_allowed_guilds = ?
            WHERE id = ?
            """.trimIndent()
        } else if (hasLfgColumns && hasTrackingColumn) {
            """
            UPDATE guilds SET name = ?, banner = ?, emoji = ?, tag = ?, home_world = ?, home_x = ?, home_y = ?, home_z = ?,
            level = ?, bank_balance = ?, mode = ?, mode_changed_at = ?,
            vault_status = ?, vault_chest_world = ?, vault_chest_x = ?, vault_chest_y = ?, vault_chest_z = ?, is_open = ?,
            join_fee_enabled = ?, join_fee_amount = ?, tracking_enabled = ?, bannerman_enabled = ?,
            ally_home_world = ?, ally_home_x = ?, ally_home_y = ?, ally_home_z = ?, ally_home_allowed_guilds = ?
            WHERE id = ?
            """.trimIndent()
        } else if (hasLfgColumns) {
            """
            UPDATE guilds SET name = ?, banner = ?, emoji = ?, tag = ?, home_world = ?, home_x = ?, home_y = ?, home_z = ?,
            level = ?, bank_balance = ?, mode = ?, mode_changed_at = ?,
            vault_status = ?, vault_chest_world = ?, vault_chest_x = ?, vault_chest_y = ?, vault_chest_z = ?, is_open = ?,
            join_fee_enabled = ?, join_fee_amount = ?, bannerman_enabled = ?,
            ally_home_world = ?, ally_home_x = ?, ally_home_y = ?, ally_home_z = ?, ally_home_allowed_guilds = ?
            WHERE id = ?
            """.trimIndent()
        } else if (hasTrackingColumn) {
            """
            UPDATE guilds SET name = ?, banner = ?, emoji = ?, tag = ?, home_world = ?, home_x = ?, home_y = ?, home_z = ?,
            level = ?, bank_balance = ?, mode = ?, mode_changed_at = ?,
            vault_status = ?, vault_chest_world = ?, vault_chest_x = ?, vault_chest_y = ?, vault_chest_z = ?, tracking_enabled = ?,
            ally_home_world = ?, ally_home_x = ?, ally_home_y = ?, ally_home_z = ?, ally_home_allowed_guilds = ?
            WHERE id = ?
            """.trimIndent()
        } else {
            """
            UPDATE guilds SET name = ?, banner = ?, emoji = ?, tag = ?, home_world = ?, home_x = ?, home_y = ?, home_z = ?,
            level = ?, bank_balance = ?, mode = ?, mode_changed_at = ?,
            vault_status = ?, vault_chest_world = ?, vault_chest_x = ?, vault_chest_y = ?, vault_chest_z = ?,
            ally_home_world = ?, ally_home_x = ?, ally_home_y = ?, ally_home_z = ?, ally_home_allowed_guilds = ?
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

            val rowsAffected = if (hasLfgColumns && hasTrackingColumn && hasBankFrozenColumn) {
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
                    if (guild.trackingEnabled) 1 else 0,
                    if (guild.bankFrozen) 1 else 0,
                    if (guild.bannermanEnabled) 1 else 0,
                    guild.allyHome?.worldId?.toString(),
                    guild.allyHome?.position?.x,
                    guild.allyHome?.position?.y,
                    guild.allyHome?.position?.z,
                    guild.allyHomeAllowedGuilds.joinToString(",") { it.toString() },
                    guild.id.toString()
                )
            } else if (hasLfgColumns && hasTrackingColumn) {
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
                    if (guild.trackingEnabled) 1 else 0,
                    if (guild.bannermanEnabled) 1 else 0,
                    guild.allyHome?.worldId?.toString(),
                    guild.allyHome?.position?.x,
                    guild.allyHome?.position?.y,
                    guild.allyHome?.position?.z,
                    guild.allyHomeAllowedGuilds.joinToString(",") { it.toString() },
                    guild.id.toString()
                )
            } else if (hasLfgColumns) {
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
                    if (guild.bannermanEnabled) 1 else 0,
                    guild.allyHome?.worldId?.toString(),
                    guild.allyHome?.position?.x,
                    guild.allyHome?.position?.y,
                    guild.allyHome?.position?.z,
                    guild.allyHomeAllowedGuilds.joinToString(",") { it.toString() },
                    guild.id.toString()
                )
            } else if (hasTrackingColumn) {
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
                    if (guild.trackingEnabled) 1 else 0,
                    guild.allyHome?.worldId?.toString(),
                    guild.allyHome?.position?.x,
                    guild.allyHome?.position?.y,
                    guild.allyHome?.position?.z,
                    guild.allyHomeAllowedGuilds.joinToString(",") { it.toString() },
                    guild.id.toString()
                )
            } else {
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
                    guild.allyHome?.worldId?.toString(),
                    guild.allyHome?.position?.x,
                    guild.allyHome?.position?.y,
                    guild.allyHome?.position?.z,
                    guild.allyHomeAllowedGuilds.joinToString(",") { it.toString() },
                    guild.id.toString()
                )
            }

            println("  rows affected: $rowsAffected")

            if (rowsAffected > 0) {
                // Persist the full named-home map; legacy home_* columns above only cover the default home.
                writeGuildHomes(guild)
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
