package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.ChronicleGoal
import net.lumalyte.lg.application.persistence.ChronicleGoalRepository
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import java.util.UUID

class ChronicleGoalRepositorySQL(private val storage: Storage<Database>) : ChronicleGoalRepository {
    override fun read(guildId: UUID): ChronicleGoal = storage.connection.connection.use { connection ->
        connection.prepareStatement("SELECT reward_id, revision FROM guild_chronicle_goals WHERE guild_id = ?").use {
            it.setString(1, guildId.toString())
            it.executeQuery().use { rows ->
                if (rows.next()) ChronicleGoal(rows.getString(1), rows.getLong(2)) else ChronicleGoal(null, 0)
            }
        }
    }

    override fun compareAndSet(guildId: UUID, expected: ChronicleGoal, rewardId: String?): Boolean =
        storage.connection.connection.use { connection ->
            connection.autoCommit = false
            try {
                // Serialize with guild deletion; SQLite's first write obtains the write lock.
                val lock = if (storage.dialect == SqlDialect.MARIADB) "SELECT id FROM guilds WHERE id = ? FOR UPDATE"
                    else "UPDATE guilds SET id = id WHERE id = ?"
                val exists = connection.prepareStatement(lock).use {
                    it.setString(1, guildId.toString())
                    if (storage.dialect == SqlDialect.MARIADB) it.executeQuery().use { rows -> rows.next() }
                    else it.executeUpdate() == 1
                }
                if (!exists) { connection.rollback(); return@use false }
                val suffix = if (storage.dialect == SqlDialect.MARIADB)
                    "ON DUPLICATE KEY UPDATE guild_id = VALUES(guild_id)" else "ON CONFLICT(guild_id) DO NOTHING"
                connection.prepareStatement("INSERT INTO guild_chronicle_goals (guild_id, reward_id, revision) VALUES (?, NULL, 0) $suffix").use {
                    it.setString(1, guildId.toString()); it.executeUpdate()
                }
                val updated = connection.prepareStatement(
                    "UPDATE guild_chronicle_goals SET reward_id = ?, revision = revision + 1 " +
                        "WHERE guild_id = ? AND revision = ? AND (reward_id = ? OR (reward_id IS NULL AND ? IS NULL))",
                ).use {
                    it.setString(1, rewardId); it.setString(2, guildId.toString()); it.setLong(3, expected.revision)
                    it.setString(4, expected.rewardId); it.setString(5, expected.rewardId)
                    it.executeUpdate() == 1
                }
                if (updated) connection.commit() else connection.rollback()
                updated
            } catch (error: Exception) {
                try { connection.rollback() } catch (rollback: Exception) { error.addSuppressed(rollback) }
                throw error
            }
        }
}
