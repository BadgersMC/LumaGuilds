package net.lumalyte.lg.infrastructure.persistence.guilds

import java.sql.Connection
import java.sql.SQLException

/** Roll back every failed block, including Errors; never implicitly commit after rollback failure. */
internal fun <T> Connection.committingTransaction(block: () -> T): T {
    val previous = autoCommit
    autoCommit = false
    var resolved = false
    try {
        val outcome = runCatching { block().also { commit() } }
        if (outcome.isSuccess) {
            resolved = true
        } else {
            val failure = checkNotNull(outcome.exceptionOrNull())
            resolved = rollbackFailure(failure)
        }
        return outcome.getOrThrow()
    } finally {
        if (resolved) autoCommit = previous else close()
    }
}

private fun Connection.rollbackFailure(failure: Throwable): Boolean =
    try {
        rollback()
        true
    } catch (rollbackFailure: SQLException) {
        failure.addSuppressed(rollbackFailure)
        false
    }
