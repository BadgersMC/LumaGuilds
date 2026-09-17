package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.RewardOwnershipRepository
import net.lumalyte.lg.domain.rewards.RewardCatalog
import net.lumalyte.lg.domain.rewards.RewardEntitlementResolver
import net.lumalyte.lg.domain.rewards.RewardOwnership
import net.lumalyte.lg.domain.rewards.RewardOwnershipRead
import net.lumalyte.lg.domain.rewards.RewardOwnershipSnapshot
import net.lumalyte.lg.domain.rewards.RewardOwnershipWrite
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID

/** New tables only. Not registered at startup until the atomic purchase/migration integration. */
class RewardOwnershipRepositorySQL(
    private val storage: Storage<Database>,
    private val catalog: RewardCatalog
) : RewardOwnershipRepository {
    private val resolver = RewardEntitlementResolver(catalog)

    init {
        val engine = if (storage.dialect == SqlDialect.MARIADB) " ENGINE=InnoDB" else ""
        storage.connection.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("""CREATE TABLE IF NOT EXISTS guild_reward_accounts (
                    guild_id VARCHAR(36) PRIMARY KEY,
                    version BIGINT NOT NULL,
                    initial_home_capacity INTEGER NOT NULL,
                    prestige_count INTEGER NOT NULL
                )$engine""")
                statement.execute("""CREATE TABLE IF NOT EXISTS guild_reward_ownership (
                    guild_id VARCHAR(36) NOT NULL,
                    reward_id VARCHAR(64) NOT NULL,
                    permanent BOOLEAN NOT NULL,
                    PRIMARY KEY (guild_id, reward_id)
                )$engine""")
            }
        }
    }

    override fun read(guildId: UUID): RewardOwnershipRead = try {
        storage.connection.connection.use { connection -> transaction(connection) {
            readSnapshot(connection, guildId)?.let { RewardOwnershipRead.Found(it) } ?: RewardOwnershipRead.Missing
        } }
    } catch (error: Exception) {
        RewardOwnershipRead.Failed(error.message ?: error.javaClass.simpleName)
    }

    override fun initialize(guildId: UUID, initialHomeCapacity: Int): RewardOwnershipWrite = try {
        val ownership = RewardOwnership(initialHomeCapacity = initialHomeCapacity)
        storage.connection.connection.use { connection -> transaction(connection) {
            connection.prepareStatement("INSERT INTO guild_reward_accounts (guild_id, version, initial_home_capacity, prestige_count) VALUES (?, 0, ?, 0)").use {
                it.setString(1, guildId.toString())
                it.setInt(2, initialHomeCapacity)
                it.executeUpdate()
            }
            require(!hasOwnedRewards(connection, guildId)) { "Orphaned reward ownership needs recovery" }
            RewardOwnershipWrite.Saved(RewardOwnershipSnapshot(0, ownership))
        } }
    } catch (error: SQLException) {
        if (error.errorCode == 19 || error.errorCode == 1062 || error.sqlState == "23505") RewardOwnershipWrite.Conflict
        else RewardOwnershipWrite.Failed(error.message ?: "SQL initialization failed")
    } catch (error: Exception) {
        RewardOwnershipWrite.Failed(error.message ?: error.javaClass.simpleName)
    }

    override fun save(guildId: UUID, expectedVersion: Long, ownership: RewardOwnership): RewardOwnershipWrite = try {
        storage.connection.connection.use { connection -> transaction(connection) {
            saveInTransaction(connection, guildId, expectedVersion, ownership)
        } }
    } catch (error: Exception) {
        RewardOwnershipWrite.Failed(error.message ?: error.javaClass.simpleName)
    }

    internal fun saveInTransaction(connection: Connection, guildId: UUID, expectedVersion: Long,
        ownership: RewardOwnership): RewardOwnershipWrite {
        check(!connection.autoCommit)
        require(expectedVersion >= 0)
        val version = Math.addExact(expectedVersion, 1)
        resolver.resolve(100, ownership)
        // Acquire the database write/row lock before reading. No read-to-write lock upgrade.
        val changed = connection.prepareStatement("UPDATE guild_reward_accounts SET version = ? WHERE guild_id = ? AND version = ?").use {
            it.setLong(1, version)
            it.setString(2, guildId.toString())
            it.setLong(3, expectedVersion)
            it.executeUpdate()
        }
        if (changed != 1) return RewardOwnershipWrite.Conflict
        val old = requireNotNull(readSnapshot(connection, guildId)).ownership
        resolver.validateTransition(old, ownership)
        connection.prepareStatement("UPDATE guild_reward_accounts SET prestige_count = ? WHERE guild_id = ?").use {
            it.setInt(1, ownership.prestigeCount)
            it.setString(2, guildId.toString())
            check(it.executeUpdate() == 1)
        }
        connection.prepareStatement("DELETE FROM guild_reward_ownership WHERE guild_id = ?").use {
            it.setString(1, guildId.toString())
            it.executeUpdate()
        }
        connection.prepareStatement("INSERT INTO guild_reward_ownership (guild_id, reward_id, permanent) VALUES (?, ?, ?)").use { statement ->
            (ownership.currentRun + ownership.permanent).sorted().forEach { id ->
                statement.setString(1, guildId.toString())
                statement.setString(2, id)
                statement.setBoolean(3, id in ownership.permanent)
                statement.addBatch()
            }
            statement.executeBatch()
        }
        // Permanent status is canonical when an ID appeared in both input sets.
        return RewardOwnershipWrite.Saved(RewardOwnershipSnapshot(version,
            ownership.copy(currentRun = ownership.currentRun - ownership.permanent)))
    }

    internal fun readSnapshot(connection: Connection, guildId: UUID): RewardOwnershipSnapshot? {
        val account = connection.prepareStatement("SELECT version, initial_home_capacity, prestige_count FROM guild_reward_accounts WHERE guild_id = ?").use {
            it.setString(1, guildId.toString())
            it.executeQuery().use { rows ->
                if (!rows.next()) {
                    require(!hasOwnedRewards(connection, guildId)) { "Orphaned reward ownership needs recovery" }
                    return null
                }
                Triple(rows.getLong("version"), rows.getInt("initial_home_capacity"), rows.getInt("prestige_count"))
            }
        }
        val current = mutableSetOf<String>()
        val permanent = mutableSetOf<String>()
        connection.prepareStatement("SELECT reward_id, permanent FROM guild_reward_ownership WHERE guild_id = ? ORDER BY reward_id").use {
            it.setString(1, guildId.toString())
            it.executeQuery().use { rows ->
                while (rows.next()) {
                    val flag = rows.getInt("permanent")
                    require(!rows.wasNull() && flag in 0..1) { "Invalid permanence value" }
                    val id = requireNotNull(rows.getString("reward_id"))
                    (if (flag == 1) permanent else current).add(id)
                }
            }
        }
        val ownership = RewardOwnership(current, permanent, account.second, account.third)
        resolver.resolve(100, ownership) // Unknown IDs or invalid permanence never turn into empty state.
        return RewardOwnershipSnapshot(account.first, ownership)
    }

    private fun hasOwnedRewards(connection: Connection, guildId: UUID): Boolean =
        connection.prepareStatement("SELECT 1 FROM guild_reward_ownership WHERE guild_id = ? LIMIT 1").use {
            it.setString(1, guildId.toString())
            it.executeQuery().use { rows -> rows.next() }
        }

    private fun <T> transaction(connection: Connection, block: () -> T): T {
        val autoCommit = connection.autoCommit
        connection.autoCommit = false
        return try {
            block().also { connection.commit() }
        } catch (error: Exception) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = autoCommit
        }
    }
}
