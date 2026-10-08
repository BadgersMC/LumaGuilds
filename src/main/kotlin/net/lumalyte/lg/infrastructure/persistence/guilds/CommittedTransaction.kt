package net.lumalyte.lg.infrastructure.persistence.guilds

import java.sql.Connection

/** Never restore autocommit (which may implicitly commit) after an unsuccessful rollback. */
internal fun <T> Connection.committingTransaction(block: () -> T): T {
    val previous = autoCommit
    autoCommit = false
    var resolved = false
    try {
        val result = block()
        commit()
        resolved = true
        return result
    } catch (error: Exception) {
        try {
            rollback()
            resolved = true
        } catch (rollbackError: Exception) {
            error.addSuppressed(rollbackError)
        }
        throw error
    } finally {
        if (resolved) autoCommit = previous else close()
    }
}
