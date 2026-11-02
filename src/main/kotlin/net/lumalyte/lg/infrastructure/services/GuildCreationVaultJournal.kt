package net.lumalyte.lg.infrastructure.services

import co.aikar.idb.Database
import net.lumalyte.lg.application.services.PhysicalGoldReservation
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID

/**
 * Durable founder-payment journal. A unique player guard is claimed in the same
 * transaction as the receipt, so two concurrent guild creations cannot both
 * reach the external Vault withdrawal. Ambiguous phases are never auto-replayed.
 */
class GuildCreationVaultJournal(private val storage: Storage<Database>) {
    data class Receipt(
        val payment: PhysicalGoldReservation,
        val phase: String,
        val createdAtEpochMillis: Long,
        val updatedAtEpochMillis: Long,
    )
    sealed interface Claim {
        data object Claimed : Claim
        data class Pending(val transactionId: UUID) : Claim
    }

    init {
        val engine = if (storage.dialect == SqlDialect.MARIADB) " ENGINE=InnoDB" else ""
        storage.connection.executeUpdate(
            "CREATE TABLE IF NOT EXISTS guild_creation_vault_payments (" +
                "transaction_id VARCHAR(36) PRIMARY KEY, player_id VARCHAR(36) NOT NULL, " +
                "amount BIGINT NOT NULL, phase VARCHAR(24) NOT NULL, " +
                "created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL)$engine"
        )
        storage.connection.executeUpdate(
            "CREATE TABLE IF NOT EXISTS guild_creation_vault_guards (" +
                "player_id VARCHAR(36) PRIMARY KEY, transaction_id VARCHAR(36) NOT NULL UNIQUE)$engine"
        )
    }

    fun pending(playerId: UUID): UUID? = storage.connection.connection.use { connection ->
        connection.prepareStatement(
            "SELECT transaction_id FROM guild_creation_vault_guards WHERE player_id = ?"
        ).use { statement ->
            statement.setString(1, playerId.toString())
            statement.executeQuery().use { rows ->
                if (rows.next()) UUID.fromString(rows.getString(1)) else null
            }
        }
    }

    fun get(transactionId: UUID): Receipt? = storage.connection.connection.use { connection ->
        connection.prepareStatement(
            "SELECT player_id, amount, phase, created_at, updated_at " +
                "FROM guild_creation_vault_payments WHERE transaction_id = ?"
        ).use { statement ->
            statement.setString(1, transactionId.toString())
            statement.executeQuery().use { rows ->
                if (!rows.next()) null else Receipt(
                    PhysicalGoldReservation(transactionId, UUID.fromString(rows.getString(1)), rows.getLong(2)),
                    rows.getString(3),
                    rows.getLong(4),
                    rows.getLong(5),
                )
            }
        }
    }

    fun claim(payment: PhysicalGoldReservation): Claim {
        pending(payment.playerId)?.let { return Claim.Pending(it) }
        return try {
            transaction { connection ->
                connection.prepareStatement(
                    "INSERT INTO guild_creation_vault_guards (player_id, transaction_id) VALUES (?, ?)"
                ).use {
                    it.setString(1, payment.playerId.toString())
                    it.setString(2, payment.id.toString())
                    check(it.executeUpdate() == 1)
                }
                connection.prepareStatement(
                    "INSERT INTO guild_creation_vault_payments " +
                        "(transaction_id, player_id, amount, phase, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'CLAIMED', ?, ?)"
                ).use {
                    val now = System.currentTimeMillis() // UTC epoch milliseconds
                    it.setString(1, payment.id.toString())
                    it.setString(2, payment.playerId.toString())
                    it.setLong(3, payment.value)
                    it.setLong(4, now)
                    it.setLong(5, now)
                    check(it.executeUpdate() == 1)
                }
                Claim.Claimed
            }
        } catch (error: SQLException) {
            // A concurrent worker may have won the unique player guard.
            // If the database is unavailable, let the failure propagate closed.
            pending(payment.playerId)?.let { Claim.Pending(it) } ?: throw error
        }
    }

    fun transition(transactionId: UUID, expected: String, next: String): Boolean =
        storage.connection.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE guild_creation_vault_payments SET phase = ?, updated_at = ? " +
                    "WHERE transaction_id = ? AND phase = ?"
            ).use {
                it.setString(1, next)
                it.setLong(2, System.currentTimeMillis())
                it.setString(3, transactionId.toString())
                it.setString(4, expected)
                it.executeUpdate() == 1
            }
        }

    /** Receipt completion and removal of the unique active-player guard commit atomically. */
    fun finish(payment: PhysicalGoldReservation, expected: String, terminal: String): Boolean =
        transaction { connection ->
            val changed = connection.prepareStatement(
                "UPDATE guild_creation_vault_payments SET phase = ?, updated_at = ? " +
                    "WHERE transaction_id = ? AND player_id = ? AND amount = ? AND phase = ?"
            ).use {
                it.setString(1, terminal)
                it.setLong(2, System.currentTimeMillis())
                it.setString(3, payment.id.toString())
                it.setString(4, payment.playerId.toString())
                it.setLong(5, payment.value)
                it.setString(6, expected)
                it.executeUpdate()
            }
            if (changed != 1) return@transaction false
            val released = connection.prepareStatement(
                "DELETE FROM guild_creation_vault_guards WHERE player_id = ? AND transaction_id = ?"
            ).use {
                it.setString(1, payment.playerId.toString())
                it.setString(2, payment.id.toString())
                it.executeUpdate()
            }
            check(released == 1) { "Vault payment guard missing; payment remains under review" }
            true
        }

    private fun <T> transaction(action: (Connection) -> T): T =
        storage.connection.connection.use { connection ->
            val autoCommit = connection.autoCommit
            connection.autoCommit = false
            var rollbackSucceeded = false
            var completed = false
            try {
                val result = action(connection)
                connection.commit()
                completed = true
                result
            } catch (error: Throwable) {
                try {
                    connection.rollback()
                    rollbackSucceeded = true
                } catch (rollback: Throwable) {
                    if (rollback !== error) error.addSuppressed(rollback)
                }
                throw error
            } finally {
                // A failed rollback must not be followed by enabling autocommit:
                // that could commit an ambiguous half-finished transaction.
                if (completed || rollbackSucceeded) connection.autoCommit = autoCommit
            }
        }
}
