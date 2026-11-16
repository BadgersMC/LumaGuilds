package net.lumalyte.lg.infrastructure.persistence.migrations

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.logging.Logger

class MigrationFailurePropagationTest {
    private val plugin = mockk<JavaPlugin>(relaxed = true) {
        every { getComponentLogger() } returns mockk<ComponentLogger>(relaxed = true)
        every { getLogger() } returns Logger.getLogger("MigrationFailurePropagationTest")
    }

    private fun brokenConnection(): Connection = mockk(relaxed = true) {
        every { createStatement() } throws SQLException("forced schema failure")
    }

    @Test
    fun `sqlite migration failure propagates before startup can continue`() {
        val connection = brokenConnection()

        val error = assertThrows(SQLException::class.java) {
            SQLiteMigrations(plugin, connection, claimsEnabled = false).migrate()
        }

        assertTrue(error.message!!.contains("startup aborted"))
        assertEquals("forced schema failure", error.cause?.message)
        verify(exactly = 1) { connection.rollback() }
        verify(exactly = 0) { connection.commit() }
    }

    @Test
    fun `mariadb migration failure propagates despite rollback attempt`() {
        val connection = brokenConnection()

        val error = assertThrows(SQLException::class.java) {
            MariaDBMigrations(plugin, connection).migrate()
        }

        assertTrue(error.message!!.contains("startup aborted"))
        assertEquals("forced schema failure", error.cause?.message)
        verify(exactly = 1) { connection.rollback() }
        verify(exactly = 0) { connection.commit() }
    }

    @Test
    fun `sqlite rejects a corrupt current-version guild schema before runtime startup`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA user_version = 49")
                statement.execute("CREATE TABLE guilds (id TEXT PRIMARY KEY)")
            }

            val error = assertThrows(SQLException::class.java) {
                SQLiteMigrations(plugin, connection, claimsEnabled = false).migrate()
            }

            assertTrue(error.message!!.contains("startup aborted"))
            assertTrue(error.cause?.message?.contains("no such column") == true)
        }
    }
}
