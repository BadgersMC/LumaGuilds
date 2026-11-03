package net.lumalyte.lg.infrastructure.persistence.migrations

import net.lumalyte.lg.infrastructure.vault.VaultLevelCapacity
import java.sql.Connection
import java.util.UUID

/**
 * Permanent vault row entitlement. Never decrement an earned capacity, including
 * across a prestige/season reset, a temporary level penalty, or empty top rows.
 */
internal object VaultCapacityUnlockSchema {
    const val VERSION = 52
    const val TABLE = "guild_vault_capacity_unlocks"

    fun create(connection: Connection, mariaDb: Boolean, backfill: Boolean) {
        val id = if (mariaDb) "VARCHAR(36)" else "TEXT"
        val engine = if (mariaDb) " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci" else ""
        connection.createStatement().use {
            it.executeUpdate("""
                CREATE TABLE IF NOT EXISTS $TABLE (
                    guild_id $id NOT NULL PRIMARY KEY,
                    capacity_slots INTEGER NOT NULL CHECK (capacity_slots BETWEEN 9 AND 54 AND capacity_slots % 9 = 0),
                    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
                )$engine
            """.trimIndent())
        }

        if (!backfill) return

        // One-time and idempotent legacy backfill: capture existing unlocked rows
        // even for guilds whose top rows are empty. Prestige and Chapter-1 history
        // independently prove full-row entitlement after the level reset.
        val hasProgression = tableExists(connection, "guild_progression")
        val levelExpression = if (hasProgression) "COALESCE(p.current_level, g.level, 1)" else "COALESCE(g.level, 1)"
        val join = if (hasProgression) " LEFT JOIN guild_progression p ON p.guild_id = g.id" else ""
        val guilds = connection.prepareStatement(
            "SELECT g.id, $levelExpression AS level FROM guilds g$join",
        ).use { query ->
            query.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(UUID.fromString(rows.getString("id")) to rows.getInt("level"))
                }
            }
        }
        guilds.forEach { (guildId, level) -> resolve(connection, guildId, VaultLevelCapacity.slots(level), mariaDb) }
    }

    /**
     * Hot read path: normally a single PK lookup, with an UPSERT only for a new
     * unlock. Deep historical reads are confined to one-time migration or a
     * missing row that needs idempotent bootstrap.
     */
    fun currentOrAdvance(connection: Connection, guildId: UUID, currentLevelSlots: Int, mariaDb: Boolean): Int {
        val stored = connection.prepareStatement("SELECT capacity_slots FROM $TABLE WHERE guild_id = ?").use {
            it.setString(1, guildId.toString())
            it.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else null }
        } ?: return resolve(connection, guildId, currentLevelSlots, mariaDb)
        check(stored in 9..54 && stored % 9 == 0) { "Invalid persisted guild vault capacity: $stored" }
        return if (currentLevelSlots > stored) {
            recordAtLeast(connection, guildId, currentLevelSlots, mariaDb)
        } else stored
    }

    /** Read historic evidence and monotonically raise the durable high-water mark. */
    fun resolve(connection: Connection, guildId: UUID, currentLevelSlots: Int, mariaDb: Boolean): Int {
        val (prestige, earnedBaseHomes) = if (tableExists(connection, "guild_reward_accounts")) {
            connection.prepareStatement(
                "SELECT prestige_count, base_home_slots_earned FROM guild_reward_accounts WHERE guild_id = ?",
            ).use { query ->
                query.setString(1, guildId.toString())
                query.executeQuery().use { rows ->
                    if (rows.next()) rows.getInt(1) to rows.getInt(2) else 0 to 1
                }
            }
        } else 0 to 1
        val occupiedTop = if (tableExists(connection, "vault_slots")) {
            connection.prepareStatement(
                "SELECT MAX(slot) FROM vault_slots WHERE guild_id = ? AND slot BETWEEN 1 AND 53",
            ).use { query ->
                query.setString(1, guildId.toString())
                query.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else 0 }
            }
        } else 0
        val beforeChapterReset = if (tableExists(connection, "chapter_migration_receipts")) {
            connection.prepareStatement(
                "SELECT MAX(before_level) FROM chapter_migration_receipts " +
                    "WHERE guild_id = ? AND migration_kind = 'CHAPTER_1_TO_2' AND dry_run = 0 AND status = 'APPLIED'",
            ).use { query ->
                query.setString(1, guildId.toString())
                query.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else 0 }
            }
        } else 0
        val evidence = maxOf(
            currentLevelSlots,
            if (prestige > 0) 54 else 9,
            // Permanent base home rewards are independent historical level proof:
            // 2 homes => level 7, 3 => 15, 4 => 20, 6 => 30.
            when {
                earnedBaseHomes >= 6 -> 54
                earnedBaseHomes >= 4 -> 45
                earnedBaseHomes >= 3 -> 36
                earnedBaseHomes >= 2 -> 27
                else -> 9
            },
            if (occupiedTop > 0) (occupiedTop / 9 + 1) * 9 else 9,
            VaultLevelCapacity.slots(beforeChapterReset),
        )
        return recordAtLeast(connection, guildId, evidence, mariaDb)
    }

    private fun tableExists(connection: Connection, name: String): Boolean =
        connection.metaData.getTables(connection.catalog, null, name, arrayOf("TABLE")).use { tables ->
            tables.next()
        }

    /** Atomic, idempotent, monotonic UPSERT; use inside the caller's transaction. */
    fun recordAtLeast(connection: Connection, guildId: UUID, slots: Int, mariaDb: Boolean): Int {
        require(slots in 9..54 && slots % 9 == 0) { "Invalid vault capacity: $slots" }
        val sql = if (mariaDb) {
            "INSERT INTO $TABLE (guild_id, capacity_slots) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE capacity_slots = GREATEST(capacity_slots, VALUES(capacity_slots))"
        } else {
            "INSERT INTO $TABLE (guild_id, capacity_slots) VALUES (?, ?) " +
                "ON CONFLICT(guild_id) DO UPDATE SET capacity_slots = MAX(capacity_slots, excluded.capacity_slots)"
        }
        connection.prepareStatement(sql).use {
            it.setString(1, guildId.toString())
            it.setInt(2, slots)
            it.executeUpdate()
        }
        return connection.prepareStatement("SELECT capacity_slots FROM $TABLE WHERE guild_id = ?").use {
            it.setString(1, guildId.toString())
            it.executeQuery().use { rows ->
                check(rows.next()) { "Guild vault capacity was not persisted" }
                rows.getInt(1)
            }
        }
    }
}
