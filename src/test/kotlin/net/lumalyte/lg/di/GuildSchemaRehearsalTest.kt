package net.lumalyte.lg.di

import io.mockk.every
import io.mockk.mockk
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.infrastructure.persistence.migrations.SQLiteMigrations
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockbukkit.mockbukkit.MockBukkit
import java.nio.file.Files
import java.nio.file.Path
import java.math.BigInteger
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager

class GuildSchemaRehearsalTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `existing schema rehearsal preserves every stored row`() {
        val source = System.getenv("ES_GUILD_REHEARSAL_DATABASE")
        assumeTrue(!source.isNullOrBlank(), "An offline database snapshot is required")
        val copy = directory.resolve("rehearsal.db")
        Files.copy(Path.of(source!!), copy)
        MockBukkit.mock()
        try {
            val plugin = mockk<LumaGuilds>(relaxed = true)
            every { plugin.getComponentLogger() } returns ComponentLogger.logger("GuildSchemaRehearsal")
            DriverManager.getConnection("jdbc:sqlite:$copy").use { connection ->
                val before = snapshot(connection)
                SQLiteMigrations(plugin, connection).migrate()
                val after = snapshot(connection, before.mapValues { it.value.columns })
                val allAfter = snapshot(connection)
                before.forEach { (table, rows) ->
                    assertEquals(rows, after[table], "Stored rows changed in $table")
                }
                (allAfter.keys - before.keys).forEach { table ->
                    if (table == "guild_homes" && "guilds" in before) {
                        assertLegacyHomesBackfilled(connection)
                    } else {
                        assertEquals(0L, allAfter.getValue(table).count, "Rehearsal populated new table $table")
                    }
                }
            }
        } finally {
            MockBukkit.unmock()
        }
    }

    @Test
    fun `rehearsal compares original columns after schema additions`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { it.execute("CREATE TABLE guilds (id INTEGER, name TEXT)") }
            connection.createStatement().use { it.execute("INSERT INTO guilds VALUES (1, 'Example')") }
            val before = snapshot(connection)
            connection.createStatement().use { it.execute("ALTER TABLE guilds ADD COLUMN status TEXT DEFAULT 'ACTIVE'") }
            assertEquals(before, snapshot(connection, before.mapValues { it.value.columns }))
            connection.createStatement().use { it.execute("UPDATE guilds SET name = 'Changed'") }
            org.junit.jupiter.api.Assertions.assertNotEquals(before, snapshot(connection, before.mapValues { it.value.columns }))
            connection.createStatement().use { it.execute("ALTER TABLE guilds DROP COLUMN name") }
            org.junit.jupiter.api.Assertions.assertThrows(AssertionError::class.java) {
                snapshot(connection, before.mapValues { it.value.columns })
            }
        }
    }

    private fun assertLegacyHomesBackfilled(connection: Connection) {
        val expected = "home_world IS NOT NULL AND home_x IS NOT NULL AND home_y IS NOT NULL AND home_z IS NOT NULL"
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM guilds WHERE $expected").use { result ->
                result.next()
                val expectedCount = result.getLong(1)
                connection.createStatement().use { homesStatement ->
                    homesStatement.executeQuery("SELECT COUNT(*) FROM guild_homes").use { homes ->
                        homes.next()
                        assertEquals(expectedCount, homes.getLong(1), "Unexpected number of backfilled guild homes")
                    }
                }
            }
            statement.executeQuery("""
                SELECT COUNT(*) FROM guilds g
                LEFT JOIN guild_homes h ON h.guild_id = g.id AND h.name = 'main'
                WHERE g.home_world IS NOT NULL AND g.home_x IS NOT NULL
                  AND g.home_y IS NOT NULL AND g.home_z IS NOT NULL
                  AND (h.guild_id IS NULL OR h.world_id IS NOT g.home_world
                    OR h.x IS NOT g.home_x OR h.y IS NOT g.home_y OR h.z IS NOT g.home_z)
            """.trimIndent()).use { result ->
                result.next()
                assertEquals(0L, result.getLong(1), "Legacy guild homes were not faithfully backfilled")
            }
        }
    }

    @Test
    fun `legacy home verification catches incorrect backfills`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE guilds (id TEXT, home_world TEXT, home_x INTEGER, home_y INTEGER, home_z INTEGER)")
                statement.execute("CREATE TABLE guild_homes (guild_id TEXT, name TEXT, world_id TEXT, x INTEGER, y INTEGER, z INTEGER)")
                statement.execute("INSERT INTO guilds VALUES ('a', 'world', 1, 2, 3), ('b', NULL, NULL, NULL, NULL)")
                statement.execute("INSERT INTO guild_homes VALUES ('a', 'main', 'world', 1, 2, 3)")
            }
            assertLegacyHomesBackfilled(connection)
            connection.createStatement().use { it.execute("UPDATE guild_homes SET x = 9") }
            org.junit.jupiter.api.Assertions.assertThrows(AssertionError::class.java) { assertLegacyHomesBackfilled(connection) }
        }
    }

    private data class TableSnapshot(val columns: List<String>, val count: Long, val sum: BigInteger, val xor: BigInteger)

    private fun snapshot(connection: Connection, projection: Map<String, List<String>>? = null): Map<String, TableSnapshot> {
        val tables = mutableListOf<String>()
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").use { result ->
                while (result.next()) tables.add(result.getString(1))
            }
        }
        if (projection != null) {
            assertEquals(emptySet<String>(), projection.keys - tables.toSet(), "Existing tables disappeared")
        }
        return (projection?.keys ?: tables.toSet()).associateWith { table ->
            val escaped = table.replace("\"", "\"\"")
            val actualColumns = mutableListOf<String>()
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA table_info(\"$escaped\")").use { result ->
                    while (result.next()) actualColumns.add(result.getString("name"))
                }
            }
            val columns = projection?.getValue(table) ?: actualColumns
            assertEquals(emptySet<String>(), columns.toSet() - actualColumns.toSet(), "Existing columns disappeared in $table")
            var count = 0L
            var sum = BigInteger.ZERO
            var xor = BigInteger.ZERO
            val mask = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE)
            val digest = MessageDigest.getInstance("SHA-256")
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT ${columns.joinToString(", ") { "\"${it.replace("\"", "\"\"")}\"" }} FROM \"$escaped\"").use { result ->
                    while (result.next()) {
                        for (column in 1..result.metaData.columnCount) {
                            val value = result.getBytes(column)
                            digest.update((value?.size ?: -1).toString().toByteArray())
                            digest.update(0.toByte())
                            if (value != null) digest.update(value)
                        }
                        val fingerprint = BigInteger(1, digest.digest())
                        count++
                        sum = sum.add(fingerprint).and(mask)
                        xor = xor.xor(fingerprint)
                    }
                }
            }
            TableSnapshot(columns, count, sum, xor)
        }
    }
}
