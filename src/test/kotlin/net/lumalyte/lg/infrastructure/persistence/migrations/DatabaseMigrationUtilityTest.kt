package net.lumalyte.lg.infrastructure.persistence.migrations

import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseMigrationUtilityTest {
    @Test
    fun `guild chat rank settings migrate immediately after guilds`() {
        assertEquals(
            listOf("guilds", GuildChatRankSettingsSchema.TABLE),
            DatabaseMigrationUtility.MIGRATION_TABLES.take(2),
        )
    }
}
