package net.lumalyte.lg.infrastructure.persistence.guilds

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.sql.Connection

/** A stable digest of an explicitly ordered persisted quest-definition projection. */
internal object QuestDefinitionFingerprint {
    data class Snapshot(val weekId: String, val questId: String, val fingerprint: String)

    /** Select the persisted definition in one statement, using an explicit stable projection. */
    fun activeBankDefinitions(connection: Connection, occurredAt: Long, lock: Boolean = false): List<Snapshot> {
        val sql = """
            SELECT q.week_id, q.quest_id, w.starts_at, w.ends_at,
                   q.name_key, q.description_key, q.action, q.target_id, q.allowed_actions,
                   q.minimum_amount, q.maximum_amount, q.natural_dimensions, q.natural_biomes,
                   q.supported_conditions, q.provenance_policy, q.target_rarity, q.target_count,
                   q.tier, q.condition_type, q.condition_value, q.conditions, q.quest_order,
                   q.experience_reward, q.item_rewards, q.leaderboard, q.leaderboard_payouts
            FROM weekly_quest_sets w JOIN weekly_quest_definitions q ON q.week_id = w.week_id
            WHERE w.active = 1 AND w.starts_at <= ? AND w.ends_at > ? AND q.action = 'DEPOSIT_BANK'
            ORDER BY q.week_id, q.quest_id
        """.trimIndent()
        return connection.prepareStatement(sql + if (lock) " FOR UPDATE" else "").use { statement ->
            statement.setLong(1, occurredAt)
            statement.setLong(2, occurredAt)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        // MariaDB does not decode numeric JDBC columns through getBytes.
                        // All projected values are scalar persisted values with stable string forms.
                        val projection = (1..rows.metaData.columnCount).map { rows.getString(it)?.toByteArray(Charsets.UTF_8) }
                        add(Snapshot(rows.getString(1), rows.getString(2), of(projection)))
                    }
                }
            }
        }
    }

    fun of(columns: List<ByteArray?>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (value in columns) {
            // Length prefixes distinguish NULL, empty fields and adjacent field boundaries.
            digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value?.size ?: -1).array())
            if (value != null) digest.update(value)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
