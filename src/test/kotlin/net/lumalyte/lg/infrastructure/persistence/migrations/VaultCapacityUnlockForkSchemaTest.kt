package net.lumalyte.lg.infrastructure.persistence.migrations

import org.junit.jupiter.api.Test
import java.sql.DriverManager
import kotlin.test.assertEquals

/**
 * REQ-250: the capacity backfill must run on this fork's schema, which has no
 * guild_reward_accounts.base_home_slots_earned column. On TEST it failed there and
 * rolled back the whole migration.
 */
class VaultCapacityUnlockForkSchemaTest {
    @Test
    fun `backfill works without upstream-only columns and keeps prestiged guilds at 54`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { c ->
            c.createStatement().use {
                it.execute("CREATE TABLE guilds (id TEXT PRIMARY KEY, level INTEGER)")
                it.execute("CREATE TABLE guild_progression (guild_id TEXT PRIMARY KEY, current_level INTEGER)")
                it.execute("CREATE TABLE guild_reward_accounts (guild_id TEXT PRIMARY KEY, prestige_count INTEGER NOT NULL)")
                it.execute("INSERT INTO guilds VALUES ('00000000-0000-0000-0000-000000000001', 1), ('00000000-0000-0000-0000-000000000002', 7)")
                it.execute("INSERT INTO guild_progression VALUES ('00000000-0000-0000-0000-000000000001', 1), ('00000000-0000-0000-0000-000000000002', 7)")
                it.execute("INSERT INTO guild_reward_accounts VALUES ('00000000-0000-0000-0000-000000000001', 6)")
            }
            VaultCapacityUnlockSchema.create(c, mariaDb = false, backfill = true)
            val slots = c.createStatement().use { s ->
                s.executeQuery("SELECT guild_id, capacity_slots FROM guild_vault_capacity_unlocks").use { rows ->
                    buildMap { while (rows.next()) put(rows.getString(1).last(), rows.getInt(2)) }
                }
            }
            assertEquals(mapOf('1' to 54, '2' to 27), slots)
        }
    }
}
