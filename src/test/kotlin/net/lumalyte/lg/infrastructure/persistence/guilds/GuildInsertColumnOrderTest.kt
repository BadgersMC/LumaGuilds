// Explicit fixture numbers document persisted coordinates, icon dimensions and approved boundaries.
@file:Suppress("MagicNumber")

package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.domain.values.Position3D
import net.lumalyte.lg.infrastructure.persistence.migrations.MariaDBMigrations
import net.lumalyte.lg.infrastructure.persistence.migrations.SQLiteMigrations
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.utils.GuiTheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** New guilds must store gui_theme and the ally-home columns in their own columns. */
internal class GuildInsertColumnOrderTest : RewardSqlTestFixture() {
    private val world = UUID.fromString("00000000-0000-0000-0000-0000000000aa")
    private val ally = UUID.fromString("00000000-0000-0000-0000-0000000000bb")

    /** Add stores theme and ally home in their own columns. */
    @DisplayName("add stores theme and ally home in their own columns")
    @Test
    fun scenario1() {
        val storage = openStorage()
        val repository = migratedRepository(storage)
        val guild = Guild(
            UUID.randomUUID(),
            "Columns",
            createdAt = Instant.now(),
            guiTheme = GuiTheme.EMBERSTONE,
            allyHome = GuildHome(world, Position3D(10, 64, -20)),
            allyHomeAllowedGuilds = setOf(ally),
        )
        assertTrue(repository.add(guild))

        assertStoredColumns(storage, guild)

        val reloaded = GuildRepositorySQLite(storage).getById(guild.id)!!
        assertEquals(GuiTheme.EMBERSTONE, reloaded.guiTheme)
        assertEquals(guild.allyHome, reloaded.allyHome)
        assertEquals(setOf(ally), reloaded.allyHomeAllowedGuilds)
    }

    private fun assertStoredColumns(storage: Storage<Database>, guild: Guild) {
        val row = storage.connection.getFirstRow(
            "SELECT gui_theme, ally_home_world, ally_home_x, ally_home_y, ally_home_z, " +
                "ally_home_allowed_guilds FROM guilds WHERE id = ?",
            guild.id.toString(),
        )
        assertEquals("EMBERSTONE", row.getString("gui_theme"))
        assertEquals(world.toString(), row.getString("ally_home_world"))
        assertEquals(10, (row.get<Any>("ally_home_x") as Number).toInt())
        assertEquals(64, (row.get<Any>("ally_home_y") as Number).toInt())
        assertEquals(-20, (row.get<Any>("ally_home_z") as Number).toInt())
        assertEquals(ally.toString(), row.getString("ally_home_allowed_guilds"))
    }

    /** Rows written by the old insert are repaired on startup. */
    @DisplayName("rows written by the old insert are repaired on startup")
    @Test
    fun scenario2() {
        val storage = openStorage()
        val repository = migratedRepository(storage)
        val guild = Guild(UUID.randomUUID(), "Legacy", createdAt = Instant.now())
        assertTrue(repository.add(guild))
        // Reproduce what the shifted insert used to store for a new guild.
        storage.connection.executeUpdate(
            "UPDATE guilds SET ally_home_world = 'NEUTRAL', ally_home_x = NULL, " +
                "ally_home_y = NULL, ally_home_z = NULL, " +
                "ally_home_allowed_guilds = NULL, gui_theme = '' WHERE id = ?",
            guild.id.toString(),
        )

        GuildRepositorySQLite(storage)

        val row = storage.connection.getFirstRow(
            "SELECT gui_theme, ally_home_world, ally_home_allowed_guilds FROM guilds WHERE id = ?",
            guild.id.toString(),
        )
        assertEquals("NEUTRAL", row.getString("gui_theme"))
        assertNull(row.getString("ally_home_world"))
        assertEquals("", row.getString("ally_home_allowed_guilds").orEmpty())
    }

    /** Repair leaves real ally homes alone. */
    @DisplayName("repair leaves real ally homes alone")
    @Test
    fun scenario3() {
        val storage = openStorage()
        val repository = migratedRepository(storage)
        val guild =
            Guild(
                UUID.randomUUID(),
                "Real",
                createdAt = Instant.now(),
                allyHome = GuildHome(world, Position3D(1, 2, 3)),
            )
        assertTrue(repository.add(guild))
        assertTrue(repository.update(guild))

        val reloaded = GuildRepositorySQLite(storage).getById(guild.id)!!
        assertEquals(guild.allyHome, reloaded.allyHome)
    }

    private fun migratedRepository(storage: Storage<Database>): GuildRepositorySQLite {
        val plugin = io.mockk.mockk<org.bukkit.plugin.java.JavaPlugin>(relaxed = true)
        io.mockk.every { plugin.getComponentLogger() } returns
            net.kyori.adventure.text.logger.slf4j.ComponentLogger.logger("GuildInsertColumnOrderTest")
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
