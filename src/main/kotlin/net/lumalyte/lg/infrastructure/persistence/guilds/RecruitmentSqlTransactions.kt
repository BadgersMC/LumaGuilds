package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.UUID

/** A failed rollback must never be followed by restoring autoCommit, which might commit uncertain writes. */
@Suppress("TooGenericExceptionCaught")
internal fun <T> Storage<Database>.recruitmentTransaction(operation: (Connection) -> T): T {
    return connection.connection.use { sql ->
        sql.autoCommit = false
        try {
            val result = operation(sql)
            sql.commit()
            result
        } catch (failure: Throwable) {
            rollbackPreservingFailure(sql, failure)
            throw failure
        }
    }
}

@Suppress("TooGenericExceptionCaught") // Keep the original failure and its rollback evidence together.
private fun rollbackPreservingFailure(connection: Connection, failure: Throwable) {
    try {
        connection.rollback()
    } catch (rollback: Throwable) {
        failure.addSuppressed(rollback)
    }
}

internal fun executeRecruitmentWrite(prepared: PreparedStatement, vararg values: Any?): Int {
    return prepared.use { statement ->
        values.forEachIndexed { index, value ->
            statement.setObject(index + 1, if (value is UUID) value.toString() else value)
        }
        statement.executeUpdate()
    }
}
