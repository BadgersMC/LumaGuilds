package net.lumalyte.lg.infrastructure.persistence.guilds

import java.sql.Connection
import java.sql.ResultSet

/** Bound JDBC writes in a caller-owned transaction. */
internal fun Connection.updateStatement(sql: String, vararg parameters: Any?): Int =
    prepareStatement(sql).use { statement ->
        parameters.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
        statement.executeUpdate()
    }

/** Read one bound row without changing transaction or connection ownership. */
internal fun <T> Connection.selectOne(sql: String, vararg parameters: Any?, mapper: (ResultSet) -> T): T? =
    prepareStatement(sql).use { statement ->
        parameters.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
        statement.executeQuery().use { rows -> if (rows.next()) mapper(rows) else null }
    }
