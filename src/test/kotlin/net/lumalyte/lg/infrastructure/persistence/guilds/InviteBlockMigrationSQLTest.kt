package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.infrastructure.persistence.migrations.InviteBlockSchema
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/** Runs schema repair against SQLite and the opt-in native MariaDB reward profile. */
internal class InviteBlockMigrationSQLTest : RewardSqlTestFixture() {
    /** A recorded schema version must not prevent repair of the missing invite block table. */
    @Test
    fun repairsMissingTable() {
        val storage = openStorage()
        migrateProductionSchema(storage)
        storage.connection.executeUpdate("DROP TABLE ${InviteBlockSchema.TABLE}")
        migrateProductionSchema(storage)
        assertTrue(InviteBlockRepositorySQL(storage).isAvailable)
    }
}
