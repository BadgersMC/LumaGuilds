package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.rewards.earnedBaseHomeSlotsAtLevel
import java.sql.Connection
import java.util.UUID

/**
 * Shared authoritative Chapter 1-style level-earned home entitlement.
 *
 * Never derive free grants from initial_home_capacity or the count/order of
 * guild_homes: both can reflect premature Chapter 2 Outpost purchases.
 */
internal object EarnedBaseHomeSlots {
    /** For grant allocators that only hold a guild ID, within the caller's SQL transaction. */
    fun resolve(connection: Connection, guildId: UUID): Int {
        val state = connection.prepareStatement(
            "SELECT base_home_slots_earned, prestige_count FROM guild_reward_accounts WHERE guild_id = ?"
        ).use {
            it.setString(1, guildId.toString())
            it.executeQuery().use { rows ->
                check(rows.next()) { "Missing reward account for earned base home slots" }
                rows.getInt("base_home_slots_earned") to rows.getInt("prestige_count")
            }
        }
        return resolve(connection, guildId, state.first, state.second)
    }

    /** For RewardOwnershipRepositorySQL, which already locked and read the account. */
    fun resolve(connection: Connection, guildId: UUID, persisted: Int, prestigeCount: Int): Int {
        require(persisted in 1..6)
        require(prestigeCount in 0..6)
        return maxOf(persisted, if (prestigeCount > 0) 6 else 1, initial(connection, guildId))
    }

    /** At account initialization before the account row exists. */
    fun initial(connection: Connection, guildId: UUID): Int =
        earnedBaseHomeSlotsAtLevel(highestLevelEvidence(connection, guildId))

    private fun highestLevelEvidence(connection: Connection, guildId: UUID): Int {
        val current = if (tableExists(connection, "guild_progression")) {
            connection.prepareStatement(
                "SELECT current_level FROM guild_progression WHERE guild_id = ?"
            ).use {
                it.setString(1, guildId.toString())
                it.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else 1 }
            }
        } else 1
        val migrated = if (tableExists(connection, "chapter_migration_receipts")) {
            connection.prepareStatement(
                "SELECT MAX(before_level) FROM chapter_migration_receipts WHERE guild_id = ? AND migration_kind = 'CHAPTER_1_TO_2' AND dry_run = 0 AND status = 'APPLIED'"
            ).use {
                it.setString(1, guildId.toString())
                it.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else 1 }
            }
        } else 1
        return maxOf(1, current, migrated)
    }

    private fun tableExists(connection: Connection, name: String): Boolean =
        connection.metaData.getTables(connection.catalog, null, "%", arrayOf("TABLE")).use { tables ->
            generateSequence { if (tables.next()) tables.getString("TABLE_NAME") else null }
                .any { it.equals(name, ignoreCase = true) }
        }
}
