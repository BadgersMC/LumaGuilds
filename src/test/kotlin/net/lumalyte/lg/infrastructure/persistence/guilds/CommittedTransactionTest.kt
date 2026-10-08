package net.lumalyte.lg.infrastructure.persistence.guilds

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.SQLException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Failure paths must preserve rollback and never commit unknown state. */
internal class CommittedTransactionTest {
    @Test fun `failed rollback disposes connection without implicitly committing unknown state`() {
        val connection = mockk<Connection>(relaxed = true)
        every { connection.autoCommit } returns true
        every { connection.rollback() } throws SQLException("rollback failed")
        val error = assertFailsWith<SQLException> { connection.committingTransaction { throw SQLException("award failed") } }
        assertEquals("award failed", error.message)
        assertEquals(1, error.suppressed.size)
        verify { connection.close() }
        verify(exactly = 0) {
            connection.autoCommit = true
            connection.commit()
        }
    }

    @Test fun errorRollsBack() {
        val connection = mockk<Connection>(relaxed = true)
        every { connection.autoCommit } returns true
        assertFailsWith<AssertionError> {
            connection.committingTransaction { throw AssertionError("failed block") }
        }
        verify { connection.rollback() }
        verify { connection.autoCommit = true }
        verify(exactly = 0) { connection.commit() }
    }
}
