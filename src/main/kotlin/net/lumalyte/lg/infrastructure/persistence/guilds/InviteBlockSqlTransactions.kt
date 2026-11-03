package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import com.zaxxer.hikari.pool.ProxyConnection
import net.lumalyte.lg.application.persistence.InviteBlockWriteOutcome
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException

/** Retains acknowledged database state even when later connection cleanup fails. */
internal data class InviteBlockSqlResult<T>(
    val outcome: InviteBlockWriteOutcome,
    val value: T?,
    val failure: Throwable?,
)

/**
 * Owns each synchronous transaction. IDB supplies the actual Hikari proxy; its JDBC unwrap returns the physical
 * delegate. SQLite abort(Executor) is a no-op, and Hikari close can recycle after a failed rollback. Therefore an
 * unresolved connection is physically closed and its logical wrapper is quarantined, never returned to the pool.
 * A rare quarantine consumes a pool slot until shutdown; reduced availability is preferable to a later commit.
 */
internal class InviteBlockSqlTransactions(private val storage: Storage<Database>) {
    private val quarantined = mutableListOf<Connection>()
    private val logger = LoggerFactory.getLogger(InviteBlockSqlTransactions::class.java)

    /** Runs once, without dispatch, retries, or any later callback. The owning repository serializes these calls. */
    fun <T> execute(operation: (Connection) -> T): InviteBlockSqlResult<T> {
        val attempt = Attempt<T>()
        try {
            val connection = storage.connection.connection
                ?: throw SQLException("Invite block storage returned no connection")
            attempt.connection = connection
            check(connection.autoCommit) { "Invite block storage supplied an unfinished transaction" }
            connection.autoCommit = false
            attempt.transactionStarted = true
            attempt.value = operation(connection)
            // A thrown commit can mean the database committed before its acknowledgement was lost.
            attempt.commitAttempted = true
            connection.commit()
            attempt.outcome = InviteBlockWriteOutcome.COMMITTED
        } catch (failure: Throwable) {
            attempt.failure = failure
            rollBack(attempt)
        } finally {
            release(attempt)
        }
        val failure = attempt.failure
        if (failure is Error) throw failure
        return InviteBlockSqlResult(attempt.outcome, attempt.value, failure)
    }

    private fun rollBack(attempt: Attempt<*>) {
        val connection = attempt.connection
        if (connection != null && attempt.transactionStarted) {
            try {
                connection.rollback()
                attempt.rollbackConfirmed = true
            } catch (rollback: Throwable) {
                attempt.record(rollback)
            }
        }
        attempt.outcome = when {
            attempt.commitAttempted -> InviteBlockWriteOutcome.UNKNOWN
            !attempt.transactionStarted || attempt.rollbackConfirmed -> InviteBlockWriteOutcome.NOT_APPLIED
            else -> InviteBlockWriteOutcome.UNKNOWN
        }
    }

    private fun release(attempt: Attempt<*>) {
        val connection = attempt.connection ?: return
        if (attempt.outcome == InviteBlockWriteOutcome.UNKNOWN || !attempt.transactionStarted) {
            quarantine(connection, attempt)
            return
        }
        try {
            // Only a confirmed commit or a confirmed rollback reaches this reset.
            connection.autoCommit = true
        } catch (reset: Throwable) {
            attempt.record(reset)
            logger.warn("Invite block connection reset failed after a resolved transaction", reset)
            quarantine(connection, attempt)
            return
        }
        try {
            connection.close()
        } catch (close: Throwable) {
            // It may already have returned to the pool. Do not close its physical delegate after that handoff.
            // The transaction is known resolved, so this cannot downgrade COMMITTED into a failed write.
            attempt.record(close)
            logger.warn("Invite block connection close failed after a resolved transaction", close)
        }
    }

    private fun quarantine(connection: Connection, attempt: Attempt<*>) {
        quarantined.add(connection)
        try {
            val physical = connection.unwrap(Connection::class.java)
            check(physical !== connection && physical !is ProxyConnection) {
                "Invite block connection did not expose its physical delegate"
            }
            physical.close()
            check(physical.isClosed) { "Invite block physical connection close was not confirmed" }
            logger.warn("Quarantined invite block connection; one pool slot is retained until shutdown")
        } catch (disposal: Throwable) {
            attempt.record(disposal)
            logger.error("Invite block connection disposal is UNKNOWN; its wrapper remains quarantined", disposal)
        }
        // Never call logical close or restore autoCommit on this path, including after physical-close failure.
    }

    private class Attempt<T> {
        var connection: Connection? = null
        var transactionStarted = false
        var commitAttempted = false
        var rollbackConfirmed = false
        var outcome = InviteBlockWriteOutcome.NOT_APPLIED
        var value: T? = null
        var failure: Throwable? = null

        fun record(error: Throwable) {
            val first = failure
            if (first == null) failure = error else if (first !== error) first.addSuppressed(error)
        }
    }
}
