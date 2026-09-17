package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.RewardStateRepository
import net.lumalyte.lg.domain.rewards.*
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import java.sql.Connection
import java.util.UUID

class RewardStateRepositorySQL(
    private val storage: Storage<Database>,
    private val owners: RewardOwnershipRepositorySQL
) : RewardStateRepository {
    override fun read(guildId: UUID): RewardStateRead = try {
        storage.connection.connection.use { connection ->
            val isolation = connection.transactionIsolation
            val autoCommit = connection.autoCommit
            try {
                // Explicit repeatable snapshot; never combine cached progression with fresh ownership.
                if (storage.dialect == SqlDialect.MARIADB)
                    connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
                connection.autoCommit = false
                val snapshot = owners.readSnapshot(connection, guildId)
                val level = connection.prepareStatement("SELECT current_level FROM guild_progression WHERE guild_id = ?").use {
                    it.setString(1, guildId.toString())
                    it.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else null }
                }
                val result = if (snapshot == null || level == null) RewardStateRead.Missing
                    else RewardStateRead.Found(level, snapshot)
                connection.commit()
                result
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = autoCommit
                if (connection.transactionIsolation != isolation) connection.transactionIsolation = isolation
            }
        }
    } catch (_: Exception) {
        RewardStateRead.Failed
    }
}
