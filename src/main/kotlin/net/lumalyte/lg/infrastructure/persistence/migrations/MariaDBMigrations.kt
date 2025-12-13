package net.lumalyte.lg.infrastructure.persistence.migrations

import net.kyori.adventure.text.Component
import org.bukkit.plugin.java.JavaPlugin
import java.sql.Connection
import java.sql.SQLException

/**
 * MariaDB/MySQL migration manager.
 * Handles database schema versioning and migrations for MariaDB.
 */
class MariaDBMigrations(private val plugin: JavaPlugin, private val connection: Connection) {
    private val componentLogger = plugin.getComponentLogger()

    fun migrate() {
        try {
            connection.autoCommit = false
            var currentDbVersion = getCurrentDatabaseVersion()
            componentLogger.info(Component.text("Current database schema version: v$currentDbVersion"))

            // Run migrations sequentially
            if (currentDbVersion < 1) {
                migrateToVersion1()
                updateDatabaseVersion(1)
                currentDbVersion = 1
            }

            connection.commit()

            val finalVersion = getCurrentDatabaseVersion()
            componentLogger.info(Component.text("✓ Database migrations completed (v$finalVersion)"))
        } catch (e: SQLException) {
            plugin.logger.severe("Database migration failed: ${e.message}")
            e.printStackTrace()
            try {
                connection.rollback()
                componentLogger.warn(Component.text("Database migration transaction rolled back"))
            } catch (rb: SQLException) {
                plugin.logger.severe("Failed to rollback database migration: ${rb.message}")
            }
            plugin.server.pluginManager.disablePlugin(plugin)
        } finally {
            connection.autoCommit = true
        }
    }

    private fun getCurrentDatabaseVersion(): Int {
        // MariaDB doesn't have PRAGMA, so we use a version table
        connection.createStatement().use { stmt ->
            // Create version table if it doesn't exist
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INT PRIMARY KEY
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """.trimIndent())

            stmt.executeQuery("SELECT version FROM schema_version LIMIT 1").use { rs ->
                return if (rs.next()) rs.getInt(1) else 0
            }
        }
    }

    private fun updateDatabaseVersion(version: Int) {
        connection.createStatement().use { stmt ->
            stmt.execute("DELETE FROM schema_version")
            stmt.execute("INSERT INTO schema_version (version) VALUES ($version)")
        }
    }

    /**
     * Initial migration - creates all tables for fresh installations.
     */
    private fun migrateToVersion1() {
        val sqlCommands = mutableListOf<String>()

        // Guilds table
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS guilds (
                id VARCHAR(36) PRIMARY KEY,
                name VARCHAR(255) NOT NULL UNIQUE,
                banner TEXT,
                emoji TEXT,
                tag TEXT,
                home_world VARCHAR(255),
                home_x INT,
                home_y INT,
                home_z INT,
                level INT NOT NULL DEFAULT 1,
                bank_balance INT NOT NULL DEFAULT 0,
                mode VARCHAR(20) NOT NULL DEFAULT 'Hostile',
                mode_changed_at DATETIME,
                created_at DATETIME NOT NULL,
                is_open BOOLEAN NOT NULL DEFAULT FALSE,
                join_fee_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                join_fee_amount INT NOT NULL DEFAULT 0,
                INDEX idx_guilds_name (name),
                INDEX idx_guilds_mode (mode),
                INDEX idx_guilds_is_open (is_open),
                INDEX idx_guilds_join_fee_enabled (join_fee_enabled)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """.trimIndent())

        // Ranks table
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS ranks (
                id VARCHAR(36) PRIMARY KEY,
                guild_id VARCHAR(36) NOT NULL,
                name VARCHAR(255) NOT NULL,
                priority INT NOT NULL DEFAULT 0,
                permissions TEXT,
                icon TEXT,
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
                INDEX idx_ranks_guild_id (guild_id),
                INDEX idx_ranks_priority (priority)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """.trimIndent())

        // Members table
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS members (
                player_id VARCHAR(36) NOT NULL,
                guild_id VARCHAR(36) NOT NULL,
                rank_id VARCHAR(36) NOT NULL,
                joined_at DATETIME NOT NULL,
                PRIMARY KEY (player_id, guild_id),
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
                FOREIGN KEY (rank_id) REFERENCES ranks(id) ON DELETE CASCADE,
                INDEX idx_members_guild_id (guild_id),
                INDEX idx_members_player_id (player_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """.trimIndent())

        // Guild invitations table
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS guild_invitations (
                guild_id VARCHAR(36) NOT NULL,
                guild_name VARCHAR(255) NOT NULL,
                invited_player_id VARCHAR(36) NOT NULL,
                inviter_player_id VARCHAR(36) NOT NULL,
                inviter_name VARCHAR(255) NOT NULL,
                timestamp DATETIME NOT NULL,
                PRIMARY KEY (invited_player_id, guild_id),
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
                INDEX idx_guild_invitations_guild_id (guild_id),
                INDEX idx_guild_invitations_invited_player_id (invited_player_id),
                INDEX idx_guild_invitations_timestamp (timestamp)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """.trimIndent())

        // Relations table (alliances, wars, etc.)
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS relations (
                guild_id VARCHAR(36) NOT NULL,
                target_guild_id VARCHAR(36) NOT NULL,
                relation_type VARCHAR(20) NOT NULL,
                created_at DATETIME NOT NULL,
                PRIMARY KEY (guild_id, target_guild_id),
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
                FOREIGN KEY (target_guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
                INDEX idx_relations_guild_id (guild_id),
                INDEX idx_relations_type (relation_type)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """.trimIndent())

        // Bank transactions table
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS bank_tx (
                id VARCHAR(36) PRIMARY KEY,
                guild_id VARCHAR(36) NOT NULL,
                actor_id VARCHAR(36) NOT NULL,
                type VARCHAR(20) NOT NULL,
                amount INT NOT NULL,
                fee INT NOT NULL DEFAULT 0,
                created_at DATETIME NOT NULL,
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
                INDEX idx_bank_tx_guild_id (guild_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """.trimIndent())

        // Kills table
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS kills (
                id VARCHAR(36) PRIMARY KEY,
                killer_id VARCHAR(36) NOT NULL,
                victim_id VARCHAR(36) NOT NULL,
                killer_guild_id VARCHAR(36),
                victim_guild_id VARCHAR(36),
                timestamp DATETIME NOT NULL,
                weapon VARCHAR(255),
                location_world VARCHAR(255),
                location_x DOUBLE,
                location_y DOUBLE,
                location_z DOUBLE,
                INDEX idx_kills_timestamp (timestamp),
                INDEX idx_kills_killer_guild_id (killer_guild_id),
                INDEX idx_kills_victim_guild_id (victim_guild_id),
                INDEX idx_kills_killer_id (killer_id),
                INDEX idx_kills_victim_id (victim_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """.trimIndent())

        // Wars table
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS wars (
                id VARCHAR(36) PRIMARY KEY,
                guild_a VARCHAR(36) NOT NULL,
                guild_b VARCHAR(36) NOT NULL,
                state VARCHAR(20) NOT NULL,
                started_at DATETIME,
                ended_at DATETIME,
                result VARCHAR(20),
                stats TEXT,
                created_at DATETIME NOT NULL,
                FOREIGN KEY (guild_a) REFERENCES guilds(id) ON DELETE CASCADE,
                FOREIGN KEY (guild_b) REFERENCES guilds(id) ON DELETE CASCADE,
                INDEX idx_wars_guild_a (guild_a),
                INDEX idx_wars_guild_b (guild_b),
                INDEX idx_wars_state (state)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """.trimIndent())

        // Leaderboards table
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS leaderboards (
                id VARCHAR(36) PRIMARY KEY,
                type VARCHAR(20) NOT NULL,
                period_start DATETIME NOT NULL,
                period_end DATETIME NOT NULL,
                data TEXT NOT NULL,
                created_at DATETIME NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """.trimIndent())

        // Parties table
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS parties (
                id VARCHAR(36) PRIMARY KEY,
                name VARCHAR(255),
                guild_ids TEXT NOT NULL,
                leader_id VARCHAR(36) NOT NULL,
                status VARCHAR(20) NOT NULL,
                created_at DATETIME NOT NULL,
                expires_at DATETIME,
                restricted_roles TEXT,
                INDEX idx_parties_leader_id (leader_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """.trimIndent())

        // Player party preferences table
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS player_party_preferences (
                player_id VARCHAR(36) PRIMARY KEY,
                party_id VARCHAR(36) NOT NULL,
                set_at DATETIME NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """.trimIndent())

        // Guild progression table
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS guild_progression (
                guild_id VARCHAR(36) PRIMARY KEY,
                total_experience BIGINT NOT NULL DEFAULT 0,
                current_level INT NOT NULL DEFAULT 1,
                experience_this_level BIGINT NOT NULL DEFAULT 0,
                experience_for_next_level BIGINT NOT NULL DEFAULT 800,
                last_level_up DATETIME,
                total_level_ups INT NOT NULL DEFAULT 0,
                unlocked_perks TEXT NOT NULL DEFAULT '',
                created_at DATETIME NOT NULL,
                last_updated DATETIME NOT NULL,
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
                INDEX idx_guild_progression_level (current_level)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """.trimIndent())

        // Experience transactions table
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS experience_transactions (
                id VARCHAR(36) PRIMARY KEY,
                guild_id VARCHAR(36) NOT NULL,
                amount INT NOT NULL,
                source VARCHAR(255) NOT NULL,
                description TEXT,
                actor_id VARCHAR(36),
                timestamp DATETIME NOT NULL,
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
                INDEX idx_experience_transactions_guild_id (guild_id),
                INDEX idx_experience_transactions_timestamp (timestamp),
                INDEX idx_experience_transactions_source (source)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """.trimIndent())

        // Guild activity metrics table
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS guild_activity_metrics (
                guild_id VARCHAR(36) PRIMARY KEY,
                member_count INT NOT NULL DEFAULT 0,
                active_members INT NOT NULL DEFAULT 0,
                claims_owned INT NOT NULL DEFAULT 0,
                claims_created_this_week INT NOT NULL DEFAULT 0,
                kills_this_week INT NOT NULL DEFAULT 0,
                deaths_this_week INT NOT NULL DEFAULT 0,
                bank_deposits_this_week INT NOT NULL DEFAULT 0,
                relations_formed INT NOT NULL DEFAULT 0,
                wars_participated INT NOT NULL DEFAULT 0,
                last_updated DATETIME NOT NULL,
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
                INDEX idx_guild_activity_metrics_member_count (member_count)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """.trimIndent())

        // Audits table
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS audits (
                id VARCHAR(36) PRIMARY KEY,
                time DATETIME NOT NULL,
                actor_id VARCHAR(36) NOT NULL,
                guild_id VARCHAR(36),
                action VARCHAR(255) NOT NULL,
                details TEXT,
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE SET NULL,
                INDEX idx_audits_guild_id (guild_id),
                INDEX idx_audits_time (time)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """.trimIndent())

        // Guild vault items table (for physical vault chest inventories)
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS guild_vault_items (
                id INT AUTO_INCREMENT PRIMARY KEY,
                guild_id VARCHAR(36) NOT NULL,
                slot_index INT NOT NULL,
                item_data TEXT NOT NULL,
                UNIQUE KEY unique_guild_slot (guild_id, slot_index),
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
                INDEX idx_vault_guild_id (guild_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """.trimIndent())

        // Claims tables (if claims are enabled)
        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS claims (
                id VARCHAR(36) PRIMARY KEY,
                world_id VARCHAR(255),
                owner_id VARCHAR(36),
                team_id VARCHAR(36),
                creation_time DATETIME,
                name VARCHAR(255),
                description TEXT,
                position_x INT,
                position_y INT,
                position_z INT,
                icon VARCHAR(255),
                INDEX idx_claims_owner_id (owner_id),
                INDEX idx_claims_team_id (team_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """.trimIndent())

        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS claim_partitions (
                id VARCHAR(36) PRIMARY KEY,
                claim_id VARCHAR(36),
                lower_position_x INT,
                lower_position_z INT,
                upper_position_x INT,
                upper_position_z INT,
                FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE,
                INDEX idx_partitions_claim_id (claim_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """.trimIndent())

        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS claim_default_permissions (
                claim_id VARCHAR(36),
                permission VARCHAR(255),
                FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE,
                UNIQUE KEY unique_claim_permission (claim_id, permission)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """.trimIndent())

        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS claim_flags (
                claim_id VARCHAR(36),
                flag VARCHAR(255),
                FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE,
                UNIQUE KEY unique_claim_flag (claim_id, flag)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """.trimIndent())

        sqlCommands.add("""
            CREATE TABLE IF NOT EXISTS claim_player_permissions (
                claim_id VARCHAR(36),
                player_id VARCHAR(36),
                permission VARCHAR(255),
                FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE,
                UNIQUE KEY unique_claim_player_permission (claim_id, player_id, permission),
                INDEX idx_player_permissions_player_id (player_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """.trimIndent())

        if (sqlCommands.isNotEmpty()) {
            executeMigrationCommands(sqlCommands)
        }
    }

    private fun executeMigrationCommands(commands: List<String>) {
        commands.forEach { sql ->
            executeSql(sql)
        }
    }

    private fun executeSql(sql: String) {
        try {
            connection.createStatement().use { stmt ->
                stmt.execute(sql)
            }
        } catch (e: SQLException) {
            plugin.logger.severe("Failed to execute SQL: $sql")
            throw e
        }
    }

    private fun tableExists(tableName: String): Boolean {
        val sql = """
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema = DATABASE()
            AND table_name = ?
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, tableName)
            stmt.executeQuery().use { rs ->
                return rs.next() && rs.getInt(1) > 0
            }
        }
    }
}
