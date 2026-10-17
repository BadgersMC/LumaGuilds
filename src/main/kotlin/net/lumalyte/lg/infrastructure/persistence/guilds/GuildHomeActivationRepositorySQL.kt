package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.GuildHomeActivationRepository
import net.lumalyte.lg.infrastructure.persistence.migrations.GuildHomeActivationSchema
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.util.UUID

class GuildHomeActivationRepositorySQL(private val storage: Storage<Database>) : GuildHomeActivationRepository {
    private val logger = LoggerFactory.getLogger(GuildHomeActivationRepositorySQL::class.java)

    init {
        storage.connection.connection.use { connection ->
            GuildHomeActivationSchema.create(connection, storage.dialect == SqlDialect.MARIADB)
        }
    }

    override fun isActive(guildId: UUID, homeName: String): Boolean = runCatching {
        storage.connection.getFirstRow(
            "SELECT 1 AS present FROM ${GuildHomeActivationSchema.ACTIVATIONS_TABLE} WHERE guild_id = ? AND home_name = ? LIMIT 1",
            guildId.toString(), homeName,
        ) != null
    }.getOrElse {
        logger.error("Failed to read home activation for $guildId/$homeName", it)
        false
    }

    override fun activeCount(guildId: UUID): Int = runCatching {
        storage.connection.getFirstRow(
            "SELECT COUNT(*) AS count FROM ${GuildHomeActivationSchema.ACTIVATIONS_TABLE} WHERE guild_id = ?",
            guildId.toString(),
        )?.getInt("count") ?: 0
    }.getOrElse {
        logger.error("Failed to count home activations for $guildId", it)
        0
    }

    override fun availableCredits(guildId: UUID): Int = runCatching {
        storage.connection.getFirstRow(
            "SELECT credits FROM ${GuildHomeActivationSchema.CREDITS_TABLE} WHERE guild_id = ?",
            guildId.toString(),
        )?.getInt("credits") ?: 0
    }.getOrElse {
        logger.error("Failed to read home activation credits for $guildId", it)
        0
    }

    override fun activate(guildId: UUID, homeName: String, transactionId: UUID?): Boolean = runCatching {
        storage.connection.connection.use { connection -> transaction(connection) {
            if (isActive(connection, guildId, homeName)) return@transaction true
            connection.prepareStatement(
                "INSERT INTO ${GuildHomeActivationSchema.ACTIVATIONS_TABLE} " +
                    "(guild_id, home_name, activated_at, transaction_id) VALUES (?, ?, ?, ?)",
            ).use {
                it.setString(1, guildId.toString())
                it.setString(2, homeName)
                it.setLong(3, System.currentTimeMillis())
                it.setString(4, transactionId?.toString())
                it.executeUpdate() == 1
            }
        } }
    }.getOrElse {
        logger.error("Failed to activate home $guildId/$homeName", it)
        false
    }

    override fun activateUsingCredit(guildId: UUID, homeName: String): Boolean = runCatching {
        storage.connection.connection.use { connection -> transaction(connection) {
            if (isActive(connection, guildId, homeName)) return@transaction true
            val consumed = connection.prepareStatement(
                "UPDATE ${GuildHomeActivationSchema.CREDITS_TABLE} SET credits = credits - 1 " +
                    "WHERE guild_id = ? AND credits > 0",
            ).use {
                it.setString(1, guildId.toString())
                it.executeUpdate() == 1
            }
            if (!consumed) return@transaction false
            connection.prepareStatement(
                "INSERT INTO ${GuildHomeActivationSchema.ACTIVATIONS_TABLE} " +
                    "(guild_id, home_name, activated_at, transaction_id) VALUES (?, ?, ?, NULL)",
            ).use {
                it.setString(1, guildId.toString())
                it.setString(2, homeName)
                it.setLong(3, System.currentTimeMillis())
                check(it.executeUpdate() == 1)
            }
            true
        } }
    }.getOrElse {
        logger.error("Failed to consume activation credit for $guildId/$homeName", it)
        false
    }

    override fun remove(guildId: UUID, homeName: String): Boolean = runCatching {
        storage.connection.executeUpdate(
            "DELETE FROM ${GuildHomeActivationSchema.ACTIVATIONS_TABLE} WHERE guild_id = ? AND home_name = ?",
            guildId.toString(), homeName,
        ) >= 0
    }.getOrElse {
        logger.error("Failed to remove home activation for $guildId/$homeName", it)
        false
    }

    override fun removeAll(guildId: UUID): Boolean = runCatching {
        storage.connection.executeUpdate(
            "DELETE FROM ${GuildHomeActivationSchema.ACTIVATIONS_TABLE} WHERE guild_id = ?",
            guildId.toString(),
        ) >= 0
    }.getOrElse {
        logger.error("Failed to remove home activations for $guildId", it)
        false
    }

    private fun isActive(connection: Connection, guildId: UUID, homeName: String): Boolean =
        connection.prepareStatement(
            "SELECT 1 FROM ${GuildHomeActivationSchema.ACTIVATIONS_TABLE} WHERE guild_id = ? AND home_name = ? LIMIT 1",
        ).use {
            it.setString(1, guildId.toString())
            it.setString(2, homeName)
            it.executeQuery().use { rows -> rows.next() }
        }

    private fun <T> transaction(connection: Connection, block: () -> T): T {
        val oldAutoCommit = connection.autoCommit
        connection.autoCommit = false
        return try {
            block().also { connection.commit() }
        } catch (error: Exception) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = oldAutoCommit
        }
    }
}
