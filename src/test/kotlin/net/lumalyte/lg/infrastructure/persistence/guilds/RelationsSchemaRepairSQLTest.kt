package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.RelationStatus
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.infrastructure.persistence.migrations.RelationsSchemaRepair
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

/** REQ-273. MariaDB only: SQLite never had the old layout. Runs in the mariaDbRewardTest task. */
class RelationsSchemaRepairSQLTest : RewardSqlTestFixture() {
    @Test
    fun `production MariaDB schema gives a relations table the repository can read and write`() {
        val storage = openStorage()
        assumeTrue(storage.dialect == SqlDialect.MARIADB)
        migrateProductionSchema(storage)
        val repository = RelationRepositorySQLite(storage)
        assertEquals(emptySet(), repository.getByGuild(UUID.randomUUID()))
    }

    @Test
    fun `an old-layout relations table is moved aside and its rows carried across`() {
        val storage = openStorage()
        assumeTrue(storage.dialect == SqlDialect.MARIADB)
        val a = UUID.fromString("00000000-0000-0000-0000-00000000000b")
        val b = UUID.fromString("00000000-0000-0000-0000-00000000000a")
        storage.connection.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """CREATE TABLE relations (guild_id VARCHAR(36) NOT NULL, target_guild_id VARCHAR(36) NOT NULL,
                       relation_type VARCHAR(20) NOT NULL, created_at DATETIME NOT NULL,
                       PRIMARY KEY (guild_id, target_guild_id))""",
                )
                statement.execute("INSERT INTO relations VALUES ('$a', '$b', 'ally', '2026-10-01 12:00:00')")
            }
            RelationsSchemaRepair.ensure(connection)
            RelationsSchemaRepair.ensure(connection)
        }
        val relation = RelationRepositorySQLite(storage).getByGuilds(a, b)!!
        assertEquals(RelationType.ALLY, relation.type)
        assertEquals(RelationStatus.ACTIVE, relation.status)
        assertEquals(setOf(a, b), setOf(relation.guildA, relation.guildB))
    }
}
