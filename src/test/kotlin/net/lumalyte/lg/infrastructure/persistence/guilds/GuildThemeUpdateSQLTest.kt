// Explicit fixture numbers document persisted coordinates, icon dimensions and approved boundaries.
@file:Suppress("MagicNumber")

package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.persistence.migrations.MariaDBMigrations
import net.lumalyte.lg.infrastructure.persistence.migrations.SQLiteMigrations
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.utils.GuiTheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** REQ-121: resetting a revoked theme touches only gui_theme, never a stale copy of the guild. */
internal class GuildThemeUpdateSQLTest : RewardSqlTestFixture() {
    /** Theme reset keeps concurrent guild changes. */
    @DisplayName("theme reset keeps concurrent guild changes")
    @Test
    fun scenario1() {
        val storage = openStorage()
        val repository = migratedRepository(storage)
        val guild = Guild(UUID.randomUUID(), "Haunted", createdAt = Instant.now(), guiTheme = GuiTheme.HALLOWEEN)
        assertTrue(repository.add(guild))
        // Another writer renames the guild after a revoke read its snapshot.
        assertTrue(repository.update(guild.copy(name = TEST_RENAMED_GUILD)))

        assertTrue(repository.updateGuiTheme(guild.id, GuiTheme.HALLOWEEN, GuiTheme.NEUTRAL))

        assertEquals(TEST_RENAMED_GUILD, repository.getById(guild.id)!!.name)
        assertEquals(GuiTheme.NEUTRAL, repository.getById(guild.id)!!.guiTheme)
        val reloaded = GuildRepositorySQLite(storage).getById(guild.id)!!
        assertEquals(TEST_RENAMED_GUILD, reloaded.name)
        assertEquals(GuiTheme.NEUTRAL, reloaded.guiTheme)
    }

    /** Theme reset only applies while the expected theme is equipped. */
    @DisplayName("theme reset only applies while the expected theme is equipped")
    @Test
    fun scenario2() {
        val storage = openStorage()
        val repository = migratedRepository(storage)
        val guild = Guild(UUID.randomUUID(), "Ember", createdAt = Instant.now(), guiTheme = GuiTheme.EMBERSTONE)
        assertTrue(repository.add(guild))
        // Themes are only ever changed through update(); add() always creates NEUTRAL guilds in production.
        assertTrue(repository.update(guild))

        assertFalse(repository.updateGuiTheme(guild.id, GuiTheme.HALLOWEEN, GuiTheme.NEUTRAL))

        assertEquals(GuiTheme.EMBERSTONE, repository.getById(guild.id)!!.guiTheme)
        assertEquals(GuiTheme.EMBERSTONE, GuildRepositorySQLite(storage).getById(guild.id)!!.guiTheme)
    }

    /** Production schema via the real migration chain, as at plugin enable. */
    private fun migratedRepository(storage: Storage<Database>): GuildRepositorySQLite {
        val plugin = io.mockk.mockk<org.bukkit.plugin.java.JavaPlugin>(relaxed = true)
        io.mockk.every { plugin.getComponentLogger() } returns
            net.kyori.adventure.text.logger.slf4j.ComponentLogger.logger("GuildThemeUpdateSQLTest")
        storage.connection.connection.use { connection ->
            if (storage.dialect == SqlDialect.MARIADB) {
                MariaDBMigrations(plugin, connection).migrate()
            } else {
                SQLiteMigrations(plugin, connection, claimsEnabled = false).migrate()
            }
        }
        return GuildRepositorySQLite(storage)
    }
}

private const val TEST_RENAMED_GUILD = "Renamed"
