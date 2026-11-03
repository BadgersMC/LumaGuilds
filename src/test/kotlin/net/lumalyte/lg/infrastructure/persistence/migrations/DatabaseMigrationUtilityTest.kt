package net.lumalyte.lg.infrastructure.persistence.migrations

import io.mockk.every
import io.mockk.mockk
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseMigrationUtilityTest {
    @Test
    fun `guild chat rank settings migrate immediately after guilds`() {
        assertEquals(
            listOf("guilds", GuildChatRankSettingsSchema.TABLE),
            DatabaseMigrationUtility.MIGRATION_TABLES.take(2),
        )
    }

    @Test
    fun `incomplete cross database migration rejects before any connection or delete`() {
        assertFalse(DatabaseMigrationUtility.MIGRATION_ENABLED)
        val plugin = mockk<JavaPlugin> {
            every { getLogger() } returns Logger.getLogger("MigrationDisabledTest")
        }
        val migrator = DatabaseMigrationUtility(
            plugin,
            File("intentionally-nonexistent-source.sqlite"),
            "invalid.example",
            3306,
            "do_not_connect",
            "no_user",
            "no_password",
        )

        val report = migrator.migrate()

        assertFalse(report.success)
        assertTrue(report.errors.any { it.contains("disabled") })
        assertTrue(report.migratedTables.isEmpty())
    }
}
