package net.lumalyte.lg.infrastructure.persistence.migrations

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WarBannerPermissionMigrationTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `existing owners and war managers receive placement permission but unrelated ranks do not`() {
        DriverManager.getConnection(
            "jdbc:sqlite:${directory.resolve("permissions.db")}"
        ).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE ranks (
                        id TEXT PRIMARY KEY,
                        guild_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        priority INTEGER NOT NULL,
                        permissions TEXT
                    )
                    """.trimIndent()
                )
                statement.execute(
                    "INSERT INTO ranks VALUES ('owner','g1','Owner',0,'MANAGE_RANKS,DECLARE_WAR')"
                )
                statement.execute(
                    "INSERT INTO ranks VALUES ('war-chief','g1','War Chief',2,'DECLARE_WAR,SEND_PINGS')"
                )
                statement.execute(
                    "INSERT INTO ranks VALUES ('builder','g1','Builder',3,'MANAGE_CLAIMS')"
                )
                statement.execute(
                    "INSERT INTO ranks VALUES ('custom-owner','g2','Founder',0,'MANAGE_RANKS')"
                )
            }

            assertEquals(3, WarBannerSchema.backfillRankPermission(connection))
            assertTrue(permissions(connection, "owner").contains("PLACE_WAR_BANNER"))
            assertTrue(permissions(connection, "war-chief").contains("PLACE_WAR_BANNER"))
            assertTrue(permissions(connection, "custom-owner").contains("PLACE_WAR_BANNER"))
            assertFalse(permissions(connection, "builder").contains("PLACE_WAR_BANNER"))
            assertEquals(0, WarBannerSchema.backfillRankPermission(connection))
        }
    }

    @Test
    fun `backfill is a no-op when legacy fixture has no ranks table`() {
        DriverManager.getConnection(
            "jdbc:sqlite:${directory.resolve("no-ranks.db")}"
        ).use { connection ->
            assertEquals(0, WarBannerSchema.backfillRankPermission(connection))
        }
    }

    private fun permissions(
        connection: java.sql.Connection,
        id: String,
    ): Set<String> = connection.prepareStatement(
        "SELECT permissions FROM ranks WHERE id = ?"
    ).use { statement ->
        statement.setString(1, id)
        statement.executeQuery().use { rows ->
            check(rows.next())
            rows.getString(1).split(',').map { it.trim() }.toSet()
        }
    }
}
