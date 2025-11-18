package net.lumalyte.lg.infrastructure.persistence.migrations

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import org.bukkit.plugin.java.JavaPlugin
import java.sql.Connection
import java.sql.SQLException

class SQLiteMigrations(private val plugin: JavaPlugin, private val connection: Connection) {
    private val componentLogger = plugin.getComponentLogger()
    fun migrate() {
        try {
            // Enable WAL mode BEFORE starting transaction (if needed for migration to v12)
            val currentDbVersion = getCurrentDatabaseVersion()
            componentLogger.info(Component.text("Current database schema version: v$currentDbVersion"))

            if (currentDbVersion < 12) {
                // Enable WAL mode before any transactions
                enableWALMode()
            }

            // Now start transaction for migrations
            connection.autoCommit = false
            var dbVersion = currentDbVersion

            // Migrate sequentially
            if (dbVersion < 2) {
                migrateToVersion2()
                updateDatabaseVersion(2)
            }
            // If you have more future migrations, add them here:
            if (dbVersion < 3) {
                migrateToVersion3()
                updateDatabaseVersion(3)
                dbVersion = 3
            }
            if (dbVersion < 4) {
                migrateToVersion4()
                updateDatabaseVersion(4)
                dbVersion = 4
            }
            if (dbVersion < 5) {
                migrateToVersion5()
                updateDatabaseVersion(5)
                dbVersion = 5
            }
            if (dbVersion < 6) {
                migrateToVersion6()
                updateDatabaseVersion(6)
                dbVersion = 6
            }
            if (dbVersion < 7) {
                migrateToVersion7()
                updateDatabaseVersion(7)
                dbVersion = 7
            }
            if (dbVersion < 8) {
                migrateToVersion8()
                updateDatabaseVersion(8)
                dbVersion = 8
            }
            if (dbVersion < 9) {
                migrateToVersion9()
                updateDatabaseVersion(9)
                dbVersion = 9
            }
            if (dbVersion < 10) {
                migrateToVersion10()
                updateDatabaseVersion(10)
                dbVersion = 10
            }
            if (dbVersion < 11) {
                migrateToVersion11()
                updateDatabaseVersion(11)
                dbVersion = 11
            }
            if (dbVersion < 12) {
                migrateToVersion12()
                updateDatabaseVersion(12)
                dbVersion = 12
            }
            if (dbVersion < 13) {
                migrateToVersion13()
                updateDatabaseVersion(13)
                dbVersion = 13
            }

            // Validate that all required tables exist, recreate if missing
            validateAndRepairSchema()

            connection.commit() // Commit transaction

            // Log migration completion quietly
            val finalVersion = getCurrentDatabaseVersion()
            componentLogger.info(Component.text("✓ Database migrations completed (v$finalVersion)"))
        } catch (e: SQLException) {
            plugin.logger.severe("Database migration failed: ${e.message}")
            e.printStackTrace()
            try {
                connection.rollback() // Rollback on failure
                componentLogger.warn(Component.text("Database migration transaction rolled back"))
            } catch (rb: SQLException) {
                plugin.logger.severe("Failed to rollback database migration: ${rb.message}")
            }
            // You might want to disable the plugin here if migration is critical
            plugin.server.pluginManager.disablePlugin(plugin)
        } finally {
            connection.autoCommit = true // Reset auto-commit
        }
    }

    private fun getCurrentDatabaseVersion(): Int {
        connection.createStatement().use { stmt ->
            stmt.executeQuery("PRAGMA user_version;").use { rs ->
                val version = if (rs.next()) rs.getInt(1) else 0
                return version
            }
        }
    }

    private fun updateDatabaseVersion(version: Int) {
        connection.createStatement().use { stmt ->
            stmt.execute("PRAGMA user_version = $version;")
        }
    }

    /**
     * Migration from version 1 to version 2.
     * Contains all the provided SQL commands.
     */
    private fun migrateToVersion2() {
        val sqlCommands = mutableListOf<String>() // Use mutable list

        // Check if this is a fresh database (no existing tables)
        val isFreshDatabase = !tableExists("claimPartitions")
        
        if (isFreshDatabase) {
            // Create fresh v2 schema
            createFreshV2Schema()
            return
        }

        // --- Step 1: Foreign Keys OFF ---
        sqlCommands.add("PRAGMA foreign_keys = OFF;")
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear() // Clear list after execution

        // --- Step 2: Rename old tables ---
        sqlCommands.add("ALTER TABLE claimPartitions RENAME TO claim_partitions;")
        sqlCommands.add("ALTER TABLE claimPermissions RENAME TO claim_default_permissions;")
        sqlCommands.add("ALTER TABLE claimRules RENAME TO claim_flags;")
        sqlCommands.add("ALTER TABLE playerAccess RENAME TO claim_player_permissions;")
        sqlCommands.add("ALTER TABLE claims RENAME TO claims_old;")
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        // --- Step 3: claims table recreation and data migration ---
        sqlCommands.add("""
            CREATE TABLE claims (
                id TEXT PRIMARY KEY,
                world_id TEXT,
                owner_id TEXT,
                creation_time TEXT,
                name TEXT,
                description TEXT,
                position_x INTEGER,
                position_y INTEGER,
                position_z INTEGER,
                icon TEXT
            );
            """.trimIndent())
        sqlCommands.add("""
            INSERT INTO claims (id, world_id, owner_id, creation_time, name, description, position_x, position_y, position_z, icon)
            SELECT id, worldId, ownerId, creationTime, name, description, positionX, positionY, positionZ, icon FROM claims_old;
            """.trimIndent())
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        // --- Step 4: claim_default_permissions (Recreate, Insert, DROP OLD, RENAME NEW) ---
        sqlCommands.add("""
            CREATE TABLE claim_default_permissions_new (
                claim_id TEXT,
                permission TEXT,
                FOREIGN KEY (claim_id) REFERENCES claims(id),
                UNIQUE (claim_id, permission)
            );
            """.trimIndent())
        sqlCommands.add("INSERT INTO claim_default_permissions_new (claim_id, permission) SELECT claimId, permission FROM claim_default_permissions;")
        executeMigrationCommands(sqlCommands) // Execute these two, then ensure cursor is cleared
        sqlCommands.clear()

        sqlCommands.add("DROP TABLE claim_default_permissions;")
        sqlCommands.add("ALTER TABLE claim_default_permissions_new RENAME TO claim_default_permissions;")
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()


        // --- Step 5: claim_flags ---
        sqlCommands.add("""
            CREATE TABLE claim_flags_new (
                claim_id TEXT,
                flag TEXT,
                FOREIGN KEY (claim_id) REFERENCES claims(id),
                UNIQUE (claim_id, flag)
            );
            """.trimIndent())
        sqlCommands.add("INSERT INTO claim_flags_new (claim_id, flag) SELECT claimId, rule FROM claim_flags;")
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        sqlCommands.add("DROP TABLE claim_flags;")
        sqlCommands.add("ALTER TABLE claim_flags_new RENAME TO claim_flags;")
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        // --- Step 6: claim_partitions ---
        sqlCommands.add("""
            CREATE TABLE claim_partitions_new (
                id TEXT PRIMARY KEY,
                claim_id TEXT,
                lower_position_x INTEGER,
                lower_position_z INTEGER,
                upper_position_x INTEGER,
                upper_position_z INTEGER
            );
            """.trimIndent())
        sqlCommands.add("INSERT INTO claim_partitions_new (id, claim_id, lower_position_x, lower_position_z, upper_position_x, upper_position_z) SELECT id, claimId, lowerPositionX, lowerPositionZ, upperPositionX, upperPositionZ FROM claim_partitions;")
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        sqlCommands.add("DROP TABLE claim_partitions;")
        sqlCommands.add("ALTER TABLE claim_partitions_new RENAME TO claim_partitions;")
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        // --- Step 7: claim_player_permissions ---
        sqlCommands.add("""
            CREATE TABLE claim_player_permissions_new (
                claim_id TEXT,
                player_id TEXT,
                permission TEXT,
                FOREIGN KEY (claim_id) REFERENCES claims(id),
                UNIQUE (claim_id, player_id, permission)
            );
            """.trimIndent())
        sqlCommands.add("INSERT INTO claim_player_permissions_new (claim_id, player_id, permission) SELECT claimId, playerId, permission FROM claim_player_permissions;")
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        sqlCommands.add("DROP TABLE claim_player_permissions;")
        sqlCommands.add("ALTER TABLE claim_player_permissions_new RENAME TO claim_player_permissions;")
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        // --- Step 8: Update data in new tables ---
        sqlCommands.add("""
            UPDATE claim_default_permissions
            SET permission = CASE
                WHEN permission = 'Build' THEN 'BUILD'
                WHEN permission = 'ContainerInspect' THEN 'CONTAINER'
                WHEN permission = 'DisplayManipulate' THEN 'DISPLAY'
                WHEN permission = 'VehicleDeploy' THEN 'VEHICLE'
                WHEN permission = 'SignEdit' THEN 'SIGN'
                WHEN permission = 'RedstoneInteract' THEN 'REDSTONE'
                WHEN permission = 'DoorOpen' THEN 'DOOR'
                WHEN permission = 'VillagerTrade' THEN 'TRADE'
                WHEN permission = 'Husbandry' THEN 'HUSBANDRY'
                WHEN permission = 'Detonate' THEN 'DETONATE'
                WHEN permission = 'EventStart' THEN 'EVENT'
                WHEN permission = 'Sleep' THEN 'SLEEP'
                ELSE permission
            END;
            """.trimIndent())
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        // --- Step 9: Drop old claims table ---
        sqlCommands.add("DROP TABLE claims_old;")
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        // --- Step 10: Foreign Keys ON ---
        sqlCommands.add("PRAGMA foreign_keys = ON;")
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

    }

    /**
     * Creates a fresh v2 schema for new databases.
     */
    private fun createFreshV2Schema() {
        
        // Create claims table
        executeSql("""
            CREATE TABLE claims (
                id TEXT PRIMARY KEY,
                world_id TEXT NOT NULL,
                owner_id TEXT NOT NULL,
                creation_time TEXT NOT NULL,
                name TEXT,
                description TEXT,
                position_x INT,
                position_y INT,
                position_z INT,
                icon TEXT
            );
            """.trimIndent())
        
        // Create claim_partitions table
        executeSql("""
            CREATE TABLE claim_partitions (
                id TEXT PRIMARY KEY,
                claim_id TEXT NOT NULL,
                lower_position_x INTEGER NOT NULL,
                lower_position_z INTEGER NOT NULL,
                upper_position_x INTEGER NOT NULL,
                upper_position_z INTEGER NOT NULL,
                FOREIGN KEY (claim_id) REFERENCES claims(id)
            );
            """.trimIndent())
        
        // Create claim_default_permissions table
        executeSql("""
            CREATE TABLE claim_default_permissions (
                claim_id TEXT,
                permission TEXT,
                FOREIGN KEY (claim_id) REFERENCES claims(id),
                UNIQUE (claim_id, permission)
            );
            """.trimIndent())
        
        // Create claim_flags table
        executeSql("""
            CREATE TABLE claim_flags (
                claim_id TEXT,
                flag TEXT,
                FOREIGN KEY (claim_id) REFERENCES claims(id),
                UNIQUE (claim_id, flag)
            );
            """.trimIndent())
        
        // Create claim_player_permissions table
        executeSql("""
            CREATE TABLE claim_player_permissions (
                claim_id TEXT,
                player_id TEXT,
                permission TEXT,
                FOREIGN KEY (claim_id) REFERENCES claims(id),
                UNIQUE (claim_id, player_id, permission)
            );
            """.trimIndent())
        
    }

    /**
     * Helper function to check if a table exists.
     */
    private fun tableExists(tableName: String): Boolean {
        return try {
            connection.createStatement().use { stmt ->
                stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName'").use { rs ->
                    rs.next()
                }
            }
        } catch (e: SQLException) {
            false
        }
    }

    /**
     * Helper function to check if a column exists in a table.
     */
    private fun columnExists(tableName: String, columnName: String): Boolean {
        return try {
            connection.createStatement().use { stmt ->
                stmt.executeQuery("PRAGMA table_info($tableName)").use { rs ->
                    while (rs.next()) {
                        if (rs.getString("name") == columnName) {
                            return true
                        }
                    }
                    false
                }
            }
        } catch (e: SQLException) {
            false
        }
    }

    /**
     * Migration from version 2 to version 3.
     * Adds team_id to claims and creates guild-related tables.
     */
    private fun migrateToVersion3() {
        val sqlCommands = mutableListOf<String>()

        // --- Step 1: Foreign Keys OFF ---
        sqlCommands.add("PRAGMA foreign_keys = OFF;")
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        // --- Step 2: Add team_id column to claims table ---
        sqlCommands.add("ALTER TABLE claims ADD COLUMN team_id TEXT;")
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        // --- Step 3: Create guilds table ---
        sqlCommands.add("""
            CREATE TABLE guilds (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
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
            """.trimIndent())
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        // --- Step 4: Create ranks table ---
        sqlCommands.add("""
            CREATE TABLE ranks (
                id TEXT PRIMARY KEY,
                guild_id TEXT NOT NULL,
                name TEXT NOT NULL,
                priority INTEGER NOT NULL DEFAULT 0,
                permissions TEXT,
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
            );
            """.trimIndent())
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        // --- Step 5: Create members table ---
        sqlCommands.add("""
            CREATE TABLE members (
                player_id TEXT NOT NULL,
                guild_id TEXT NOT NULL,
                rank_id TEXT NOT NULL,
                joined_at TEXT NOT NULL,
                PRIMARY KEY (player_id, guild_id),
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
                FOREIGN KEY (rank_id) REFERENCES ranks(id) ON DELETE CASCADE
            );
            """.trimIndent())
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        // --- Step 6: Create relations table ---
        sqlCommands.add("""
            CREATE TABLE relations (
                id TEXT PRIMARY KEY,
                guild_a TEXT NOT NULL,
                guild_b TEXT NOT NULL,
                type TEXT NOT NULL CHECK (type IN ('Ally', 'Enemy', 'Truce', 'Neutral')),
                expires_at TEXT,
                created_at TEXT NOT NULL,
                FOREIGN KEY (guild_a) REFERENCES guilds(id) ON DELETE CASCADE,
                FOREIGN KEY (guild_b) REFERENCES guilds(id) ON DELETE CASCADE,
                UNIQUE (guild_a, guild_b)
            );
            """.trimIndent())
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        // --- Step 7: Create parties table ---
        sqlCommands.add("""
            CREATE TABLE parties (
                id TEXT PRIMARY KEY,
                name TEXT,
                guild_ids TEXT NOT NULL,
                leader_id TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at TEXT NOT NULL,
                expires_at TEXT
            );
            """.trimIndent())
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        // --- Step 8: Create bank_tx table ---
        sqlCommands.add("""
            CREATE TABLE bank_tx (
                id TEXT PRIMARY KEY,
                guild_id TEXT NOT NULL,
                actor_id TEXT NOT NULL,
                type TEXT NOT NULL CHECK (type IN ('Deposit', 'Withdraw')),
                amount INTEGER NOT NULL,
                fee INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL,
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
            );
            """.trimIndent())
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        // --- Step 9: Create kills table ---
        sqlCommands.add("""
            CREATE TABLE kills (
                id TEXT PRIMARY KEY,
                killer_guild TEXT,
                victim_guild TEXT,
                killer_id TEXT NOT NULL,
                victim_id TEXT NOT NULL,
                created_at TEXT NOT NULL,
                context TEXT,
                FOREIGN KEY (killer_guild) REFERENCES guilds(id) ON DELETE SET NULL,
                FOREIGN KEY (victim_guild) REFERENCES guilds(id) ON DELETE SET NULL
            );
            """.trimIndent())
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        // --- Step 10: Create wars table ---
        sqlCommands.add("""
            CREATE TABLE wars (
                id TEXT PRIMARY KEY,
                guild_a TEXT NOT NULL,
                guild_b TEXT NOT NULL,
                state TEXT NOT NULL CHECK (state IN ('Declared', 'Accepted', 'Active', 'Resolved')),
                started_at TEXT,
                ended_at TEXT,
                result TEXT CHECK (result IN ('Victory_A', 'Victory_B', 'Draw', 'Cancelled')),
                stats TEXT,
                created_at TEXT NOT NULL,
                FOREIGN KEY (guild_a) REFERENCES guilds(id) ON DELETE CASCADE,
                FOREIGN KEY (guild_b) REFERENCES guilds(id) ON DELETE CASCADE
            );
            """.trimIndent())
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        // --- Step 11: Create leaderboards table ---
        sqlCommands.add("""
            CREATE TABLE leaderboards (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                period_start TEXT NOT NULL,
                period_end TEXT NOT NULL,
                data TEXT NOT NULL,
                created_at TEXT NOT NULL
            );
            """.trimIndent())
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        // --- Step 12: Create audits table ---
        sqlCommands.add("""
            CREATE TABLE audits (
                id TEXT PRIMARY KEY,
                time TEXT NOT NULL,
                actor_id TEXT NOT NULL,
                guild_id TEXT,
                action TEXT NOT NULL,
                details TEXT,
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE SET NULL
            );
            """.trimIndent())
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        // --- Step 13: Create indices for performance ---
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_claims_team_id ON claims(team_id);")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_ranks_guild_id ON ranks(guild_id);")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_members_guild_id ON members(guild_id);")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_members_player_id ON members(player_id);")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_relations_guild_a ON relations(guild_a);")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_relations_guild_b ON relations(guild_b);")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_relations_type ON relations(type);")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_bank_tx_guild_id ON bank_tx(guild_id);")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_kills_killer_guild ON kills(killer_guild);")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_kills_victim_guild ON kills(victim_guild);")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_wars_guild_a ON wars(guild_a);")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_wars_guild_b ON wars(guild_b);")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_wars_state ON wars(state);")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_audits_guild_id ON audits(guild_id);")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_audits_time ON audits(time);")
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        // --- Step 14: Foreign Keys ON ---
        sqlCommands.add("PRAGMA foreign_keys = ON;")
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

    }

    /**
     * Migration from version 3 to version 4.
     * Adds tag and emoji columns to guilds table for enhanced display customization.
     */
    private fun migrateToVersion4() {
        val sqlCommands = mutableListOf<String>()

        // --- Step 1: Add tag column to guilds table (if not exists) ---
        if (!columnExists("guilds", "tag")) {
            sqlCommands.add("ALTER TABLE guilds ADD COLUMN tag TEXT;")
        }
        if (sqlCommands.isNotEmpty()) {
            executeMigrationCommands(sqlCommands)
            sqlCommands.clear()
        }

        // --- Step 2: Add emoji column to guilds table (if not exists) ---
        if (!columnExists("guilds", "emoji")) {
            sqlCommands.add("ALTER TABLE guilds ADD COLUMN emoji TEXT;")
        }
        if (sqlCommands.isNotEmpty()) {
            executeMigrationCommands(sqlCommands)
            sqlCommands.clear()
        }

    }

    /**
     * Migration from version 4 to version 5.
     * Fixes the parties table schema by adding missing columns.
     */
    private fun migrateToVersion5() {
        val sqlCommands = mutableListOf<String>()

        // Add missing columns to parties table (only if they don't exist)
        if (!columnExists("parties", "name")) {
            sqlCommands.add("ALTER TABLE parties ADD COLUMN name TEXT;")
        }
        if (!columnExists("parties", "leader_id")) {
            sqlCommands.add("ALTER TABLE parties ADD COLUMN leader_id TEXT NOT NULL DEFAULT '';")
        }
        if (!columnExists("parties", "status")) {
            sqlCommands.add("ALTER TABLE parties ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE';")
        }
        if (!columnExists("parties", "expires_at")) {
            sqlCommands.add("ALTER TABLE parties ADD COLUMN expires_at TEXT;")
        }

        if (sqlCommands.isNotEmpty()) {
            executeMigrationCommands(sqlCommands)
        }
    }

    /**
     * Migration from version 5 to version 6.
     * Adds icon column to ranks table.
     */
    private fun migrateToVersion6() {
        val sqlCommands = mutableListOf<String>()

        // Add icon column to ranks table (only if it doesn't exist)
        if (!columnExists("ranks", "icon")) {
            sqlCommands.add("ALTER TABLE ranks ADD COLUMN icon TEXT;")
        }

        if (sqlCommands.isNotEmpty()) {
            executeMigrationCommands(sqlCommands)
        }
    }

    /**
     * Migration from version 6 to version 7.
     * Adds restricted_roles column to parties table.
     */
    private fun migrateToVersion7() {
        val sqlCommands = mutableListOf<String>()

        // Add restricted_roles column to parties table (only if it doesn't exist)
        if (!columnExists("parties", "restricted_roles")) {
            sqlCommands.add("ALTER TABLE parties ADD COLUMN restricted_roles TEXT;")
        }

        if (sqlCommands.isNotEmpty()) {
            executeMigrationCommands(sqlCommands)
        }
    }

    /**
     * Migration from version 7 to version 8.
     * Adds player_party_preferences table for persistent party chat preferences.
     */
    private fun migrateToVersion8() {
        val sqlCommands = mutableListOf<String>()

        // Create player_party_preferences table
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS player_party_preferences (
                player_id TEXT PRIMARY KEY,
                party_id TEXT NOT NULL,
                set_at TEXT NOT NULL
            )
        """.trimIndent())

        if (sqlCommands.isNotEmpty()) {
            executeMigrationCommands(sqlCommands)
        }
    }

    /**
     * Migration from version 8 to version 9.
     * Adds guild progression system tables.
     */
    private fun migrateToVersion9() {
        val sqlCommands = mutableListOf<String>()

        // Create guild_progression table
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS guild_progression (
                guild_id TEXT PRIMARY KEY,
                total_experience INTEGER NOT NULL DEFAULT 0,
                current_level INTEGER NOT NULL DEFAULT 1,
                experience_this_level INTEGER NOT NULL DEFAULT 0,
                experience_for_next_level INTEGER NOT NULL DEFAULT 800,
                last_level_up TEXT,
                total_level_ups INTEGER NOT NULL DEFAULT 0,
                unlocked_perks TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL,
                last_updated TEXT NOT NULL,
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
            )
        """.trimIndent())

        // Create experience_transactions table
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS experience_transactions (
                id TEXT PRIMARY KEY,
                guild_id TEXT NOT NULL,
                amount INTEGER NOT NULL,
                source TEXT NOT NULL,
                description TEXT,
                actor_id TEXT,
                timestamp TEXT NOT NULL,
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
            )
        """.trimIndent())

        // Create guild_activity_metrics table
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS guild_activity_metrics (
                guild_id TEXT PRIMARY KEY,
                member_count INTEGER NOT NULL DEFAULT 0,
                active_members INTEGER NOT NULL DEFAULT 0,
                claims_owned INTEGER NOT NULL DEFAULT 0,
                claims_created_this_week INTEGER NOT NULL DEFAULT 0,
                kills_this_week INTEGER NOT NULL DEFAULT 0,
                deaths_this_week INTEGER NOT NULL DEFAULT 0,
                bank_deposits_this_week INTEGER NOT NULL DEFAULT 0,
                relations_formed INTEGER NOT NULL DEFAULT 0,
                wars_participated INTEGER NOT NULL DEFAULT 0,
                last_updated TEXT NOT NULL,
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
            )
        """.trimIndent())

        // Create indices for performance
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_guild_progression_level ON guild_progression(current_level)")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_experience_transactions_guild_id ON experience_transactions(guild_id)")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_experience_transactions_timestamp ON experience_transactions(timestamp)")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_experience_transactions_source ON experience_transactions(source)")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_guild_activity_metrics_member_count ON guild_activity_metrics(member_count)")

        if (sqlCommands.isNotEmpty()) {
            executeMigrationCommands(sqlCommands)
        }
    }

    /**
     * Migration from version 9 to version 10.
     * Updates kills table schema to match KillRepositorySQLite expectations.
     * Renames killer_guild -> killer_guild_id, victim_guild -> victim_guild_id
     * and updates other column names to match the new schema.
     */
    private fun migrateToVersion10() {
        val sqlCommands = mutableListOf<String>()

        // Check if we need to migrate the kills table
        if (tableExists("kills")) {
            // Check if old column names exist
            if (columnExists("kills", "killer_guild") && !columnExists("kills", "killer_guild_id")) {
                // --- Step 1: Foreign Keys OFF ---
                sqlCommands.add("PRAGMA foreign_keys = OFF;")
                executeMigrationCommands(sqlCommands)
                sqlCommands.clear()

                // --- Step 2: Create new kills table with correct schema ---
                sqlCommands.add("""
                    CREATE TABLE kills_new (
                        id TEXT PRIMARY KEY,
                        killer_id TEXT NOT NULL,
                        victim_id TEXT NOT NULL,
                        killer_guild_id TEXT,
                        victim_guild_id TEXT,
                        timestamp TEXT NOT NULL,
                        weapon TEXT,
                        location_world TEXT,
                        location_x REAL,
                        location_y REAL,
                        location_z REAL
                    )
                """.trimIndent())
                executeMigrationCommands(sqlCommands)
                sqlCommands.clear()

                // --- Step 3: Copy data from old table to new table ---
                sqlCommands.add("""
                    INSERT INTO kills_new (id, killer_id, victim_id, killer_guild_id, victim_guild_id, timestamp, weapon, location_world, location_x, location_y, location_z)
                    SELECT
                        id,
                        killer_id,
                        victim_id,
                        killer_guild,
                        victim_guild,
                        COALESCE(created_at, datetime('now')),
                        context,
                        NULL,
                        NULL,
                        NULL,
                        NULL
                    FROM kills
                """.trimIndent())
                executeMigrationCommands(sqlCommands)
                sqlCommands.clear()

                // --- Step 4: Drop old table and rename new table ---
                sqlCommands.add("DROP TABLE kills;")
                sqlCommands.add("ALTER TABLE kills_new RENAME TO kills;")
                executeMigrationCommands(sqlCommands)
                sqlCommands.clear()

                // --- Step 5: Create indices ---
                sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_kills_timestamp ON kills(timestamp);")
                sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_kills_killer_guild_id ON kills(killer_guild_id);")
                sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_kills_victim_guild_id ON kills(victim_guild_id);")
                sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_kills_killer_id ON kills(killer_id);")
                sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_kills_victim_id ON kills(victim_id);")
                executeMigrationCommands(sqlCommands)
                sqlCommands.clear()

                // --- Step 6: Foreign Keys ON ---
                sqlCommands.add("PRAGMA foreign_keys = ON;")
                executeMigrationCommands(sqlCommands)
                sqlCommands.clear()
            }
        }

        // Drop old indices if they exist with old column names
        try {
            connection.createStatement().use { stmt ->
                stmt.execute("DROP INDEX IF EXISTS idx_kills_killer_guild;")
                stmt.execute("DROP INDEX IF EXISTS idx_kills_victim_guild;")
            }
        } catch (e: SQLException) {
            // Ignore errors for non-existent indices
        }
    }

    /**
     * Migration from version 10 to version 11.
     * Adds guild_invitations table for persistent invitation storage.
     */
    private fun migrateToVersion11() {
        val sqlCommands = mutableListOf<String>()

        // Create guild_invitations table
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS guild_invitations (
                guild_id TEXT NOT NULL,
                guild_name TEXT NOT NULL,
                invited_player_id TEXT NOT NULL,
                inviter_player_id TEXT NOT NULL,
                inviter_name TEXT NOT NULL,
                timestamp TEXT NOT NULL,
                PRIMARY KEY (invited_player_id, guild_id),
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
            )
        """.trimIndent())

        // Create indices for performance
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_guild_invitations_guild_id ON guild_invitations(guild_id)")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_guild_invitations_invited_player_id ON guild_invitations(invited_player_id)")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_guild_invitations_timestamp ON guild_invitations(timestamp)")

        if (sqlCommands.isNotEmpty()) {
            executeMigrationCommands(sqlCommands)
        }
    }

    /**
     * Migration from version 11 to version 12.
     * Creates new vault system with WAL mode, gold balance, and transaction logging.
     */
    private fun migrateToVersion12() {
        val sqlCommands = mutableListOf<String>()

        // --- Step 1: Create vault_slots table ---
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS vault_slots (
                guild_id TEXT NOT NULL,
                slot INTEGER NOT NULL,
                item_data TEXT,
                last_modified INTEGER NOT NULL,
                PRIMARY KEY (guild_id, slot),
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
            )
        """.trimIndent())
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        // --- Step 3: Create vault_gold table ---
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS vault_gold (
                guild_id TEXT PRIMARY KEY,
                balance INTEGER NOT NULL DEFAULT 0,
                last_modified INTEGER NOT NULL,
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
            )
        """.trimIndent())
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        // --- Step 4: Create vault_transaction_log table ---
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS vault_transaction_log (
                id TEXT PRIMARY KEY,
                guild_id TEXT NOT NULL,
                player_id TEXT NOT NULL,
                transaction_type TEXT NOT NULL,
                amount INTEGER,
                item_data TEXT,
                slot INTEGER,
                timestamp INTEGER NOT NULL,
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
            )
        """.trimIndent())
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        // --- Step 5: Create indices for performance ---
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_vault_slots_guild_id ON vault_slots(guild_id)")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_vault_gold_guild_id ON vault_gold(guild_id)")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_vault_transaction_log_guild_id ON vault_transaction_log(guild_id)")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_vault_transaction_log_timestamp ON vault_transaction_log(timestamp)")
        sqlCommands.add("CREATE INDEX IF NOT EXISTS idx_vault_transaction_log_player_id ON vault_transaction_log(player_id)")
        executeMigrationCommands(sqlCommands)
        sqlCommands.clear()

        componentLogger.info(Component.text("✓ Created new vault system tables with WAL mode"))
    }

    /**
     * Migration from version 12 to version 13.
     * Adds vault status and vault chest location columns to guilds table.
     */
    private fun migrateToVersion13() {
        val sqlCommands = mutableListOf<String>()

        // Add vault_status column to guilds table (if not exists)
        if (!columnExists("guilds", "vault_status")) {
            sqlCommands.add("ALTER TABLE guilds ADD COLUMN vault_status TEXT DEFAULT 'NEVER_PLACED';")
        }

        // Add vault_chest_world column to guilds table (if not exists)
        if (!columnExists("guilds", "vault_chest_world")) {
            sqlCommands.add("ALTER TABLE guilds ADD COLUMN vault_chest_world TEXT;")
        }

        // Add vault_chest_x column to guilds table (if not exists)
        if (!columnExists("guilds", "vault_chest_x")) {
            sqlCommands.add("ALTER TABLE guilds ADD COLUMN vault_chest_x INTEGER;")
        }

        // Add vault_chest_y column to guilds table (if not exists)
        if (!columnExists("guilds", "vault_chest_y")) {
            sqlCommands.add("ALTER TABLE guilds ADD COLUMN vault_chest_y INTEGER;")
        }

        // Add vault_chest_z column to guilds table (if not exists)
        if (!columnExists("guilds", "vault_chest_z")) {
            sqlCommands.add("ALTER TABLE guilds ADD COLUMN vault_chest_z INTEGER;")
        }

        if (sqlCommands.isNotEmpty()) {
            executeMigrationCommands(sqlCommands)
        }

        componentLogger.info(Component.text("✓ Added vault status and location columns to guilds table"))
    }

    /**
     * Validates that all required tables exist and recreates them if missing.
     * This handles cases where the schema version is correct but tables are missing.
     */
    private fun validateAndRepairSchema() {
        val requiredTables = listOf(
            "guilds", "members", "relations", "parties", "party_requests",
            "player_party_preferences", "bank_tx", "kills",
            "audits", "wars", "leaderboards", "guild_vault_items", "guild_invitations",
            "vault_slots", "vault_gold", "vault_transaction_log"
        )
        // Note: chat_visibility_settings and chat_rate_limits are created by ChatSettingsRepository itself

        val missingTables = mutableListOf<String>()

        for (table in requiredTables) {
            val exists = connection.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'")
                rs.next()
            }
            if (!exists) {
                missingTables.add(table)
            }
        }

        if (missingTables.isNotEmpty()) {
            componentLogger.warn(Component.text("⚠ Missing tables detected: ${missingTables.joinToString(", ")}"))
            componentLogger.info(Component.text("🔧 Recreating missing tables..."))

            // Recreate missing tables by running the appropriate migration
            // Since we use CREATE TABLE IF NOT EXISTS, we can safely call the migration again
            if ("guild_invitations" in missingTables) {
                migrateToVersion11()
                componentLogger.info(Component.text("✓ Recreated guild_invitations table"))
            }
            if ("vault_slots" in missingTables || "vault_gold" in missingTables || "vault_transaction_log" in missingTables) {
                migrateToVersion12()
                componentLogger.info(Component.text("✓ Recreated vault system tables"))
            }
        }
    }

    /**
     * Helper function to execute a list of SQL commands sequentially.
     */
    private fun executeMigrationCommands(commands: List<String>) {
        commands.forEachIndexed { index, sql ->
            executeSql(sql)
        }
    }

    /**
     * Helper function to execute a single SQL command with error handling,
     * ensuring ResultSet/Statement are closed.
     */
    private fun executeSql(sql: String) {
        connection.createStatement().use { stmt ->
            try {
                val hasResultSet = stmt.execute(sql) // Execute and check if a ResultSet is produced
                if (hasResultSet) {
                    // If a ResultSet exists, consume and close it immediately.
                    // This is crucial for DDL following a SELECT or INSERT...SELECT.
                    stmt.resultSet?.close()
                }
            } catch (e: SQLException) {
                plugin.logger.severe("Failed to execute SQL: ${sql.substringBefore(';')}. Error: ${e.message}")
                throw e
            }
        }
    }

    /**
     * Enables WAL (Write-Ahead Logging) mode for crash-resistant vault storage.
     * Must be called BEFORE any transaction is started.
     */
    private fun enableWALMode() {
        try {
            connection.createStatement().use { stmt ->
                stmt.execute("PRAGMA journal_mode=WAL")
                stmt.execute("PRAGMA synchronous=NORMAL")
                stmt.execute("PRAGMA wal_autocheckpoint=1000")
            }
            componentLogger.info(Component.text("✓ Enabled WAL mode for crash-resistant vault storage"))
        } catch (e: SQLException) {
            componentLogger.warn(Component.text("⚠ Could not enable WAL mode: ${e.message}"))
            componentLogger.warn(Component.text("  Vault will use rollback journal mode (less crash-resistant)"))
        }
    }
}
