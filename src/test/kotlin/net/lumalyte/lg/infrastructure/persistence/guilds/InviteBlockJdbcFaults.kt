package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException

/** One-shot faults around real IDB/Hikari/JDBC operations, armed only after repository preload. */
internal class InviteBlockJdbcFaults(original: Storage<Database>) {
    enum class Fault {
        CLOSE_AFTER_COMMIT,
        RESET_AFTER_COMMIT,
        COMMIT_BEFORE_ACK,
        COMMIT_AFTER_ACK,
        STATEMENT_CLOSE,
        ROLLBACK_FAILURE,
        PHYSICAL_CLOSE_FAILURE,
    }

    var next: Fault? = null
    var physical: Connection? = null
        private set
    var logicalCloseCalls = 0
        private set
    var autoCommitResets = 0
        private set

    val storage: Storage<Database> = object : Storage<Database> {
        override val dialect = original.dialect
        override val connection = proxy<Database> { method, arguments ->
            val returned = forward(original.connection, method, arguments)
            val fault = next
            if (method.name == "getConnection" && fault != null) {
                next = null
                faultedConnection(returned as Connection, fault)
            } else returned
        }
    }

    private fun faultedConnection(original: Connection, fault: Fault): Connection {
        physical = original.unwrap(Connection::class.java)
        return proxy { method, arguments ->
            when (method.name) {
                "commit" -> commit(original, method, arguments, fault)
                "rollback" -> {
                    if (fault == Fault.ROLLBACK_FAILURE || fault == Fault.PHYSICAL_CLOSE_FAILURE) {
                        throw SQLException("Injected rollback failure")
                    }
                    forward(original, method, arguments)
                }
                "close" -> {
                    logicalCloseCalls++
                    val returned = forward(original, method, arguments)
                    if (fault == Fault.CLOSE_AFTER_COMMIT) throw SQLException("Injected error after logical close")
                    returned
                }
                "setAutoCommit" -> {
                    if (arguments.firstOrNull() == true) autoCommitResets++
                    if (arguments.firstOrNull() == true && fault == Fault.RESET_AFTER_COMMIT) {
                        throw SQLException("Injected error before post-commit connection reset")
                    }
                    forward(original, method, arguments)
                }
                "prepareStatement" -> statement(forward(original, method, arguments) as PreparedStatement, fault)
                "unwrap" -> {
                    val returned = forward(original, method, arguments)
                    if (fault == Fault.PHYSICAL_CLOSE_FAILURE && arguments.firstOrNull() == Connection::class.java) {
                        physicalCloseFailure(returned as Connection)
                    } else returned
                }
                else -> forward(original, method, arguments)
            }
        }
    }

    private fun commit(original: Connection, method: Method, arguments: Array<out Any?>, fault: Fault): Any? {
        if (fault == Fault.COMMIT_BEFORE_ACK) throw SQLException("Injected error before delegated commit")
        val returned = forward(original, method, arguments)
        if (fault == Fault.COMMIT_AFTER_ACK) throw SQLException("Injected lost commit acknowledgement")
        return returned
    }

    private fun statement(original: PreparedStatement, fault: Fault): PreparedStatement =
        proxy { method, arguments ->
            val returned = forward(original, method, arguments)
            if (method.name == "close" && fault in STATEMENT_CLOSE_FAULTS) {
                throw SQLException("Injected statement-close failure after execution")
            }
            returned
        }

    private fun physicalCloseFailure(original: Connection): Connection = proxy { method, arguments ->
        if (method.name == "close") throw SQLException("Injected physical-close failure")
        forward(original, method, arguments)
    }

    private companion object {
        val STATEMENT_CLOSE_FAULTS = setOf(Fault.STATEMENT_CLOSE, Fault.ROLLBACK_FAILURE, Fault.PHYSICAL_CLOSE_FAILURE)
    }
}

private inline fun <reified T> proxy(crossinline call: (Method, Array<out Any?>) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, arguments ->
        call(method, arguments ?: emptyArray())
    } as T

// JDBC reflection forwards a small, test-owned argument array; preserve the actual driver exception.
@Suppress("SpreadOperator")
private fun forward(receiver: Any, method: Method, arguments: Array<out Any?>): Any? =
    try {
        method.invoke(receiver, *arguments)
    } catch (failure: InvocationTargetException) {
        throw failure.targetException
    }
