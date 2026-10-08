package net.lumalyte.lg.infrastructure.persistence.guilds

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class GuildDirectoryDetailsTest : RewardSqlTestFixture() {
    @Test fun ownersAndActiveAllies() {
        val storage = openStorage()
        try {
            val sql = storage.connection
            createSchema(sql)
            val guild = UUID.randomUUID()
            val ally = UUID.randomUUID()
            val owner = UUID.randomUUID()
            sql.executeUpdate("INSERT INTO guilds VALUES (?, 'Closed'), (?, 'Ally')", guild.toString(), ally.toString())
            sql.executeUpdate(
                "INSERT INTO ranks VALUES ('owner', ?, 0), ('member', ?, 1)",
                guild.toString(),
                guild.toString(),
            )
            sql.executeUpdate(
                "INSERT INTO members VALUES (?, ?, 'owner'), (?, ?, 'member')",
                guild.toString(),
                owner.toString(),
                guild.toString(),
                UUID.randomUUID().toString(),
            )
            sql.executeUpdate(
                "INSERT INTO relations VALUES (?, ?, 'ALLY', 'ACTIVE', NULL)",
                guild.toString(),
                ally.toString(),
            )
            val repository = GuildListRepositorySQL(storage)
            assertEquals(listOf(owner), repository.getDetails(setOf(guild)).getValue(guild).owners)
            assertEquals(listOf("Ally"), repository.getDetails(setOf(guild)).getValue(guild).allies)
            sql.executeUpdate("UPDATE relations SET status = 'PENDING'")
            assertEquals(emptyList(), repository.getDetails(setOf(guild)).getValue(guild).allies)
            sql.executeUpdate("DELETE FROM members WHERE rank_id = 'owner'")
            assertEquals(emptyList(), repository.getDetails(setOf(guild)).getValue(guild).owners)
            assertFailsWith<IllegalArgumentException> {
                repository.getDetails((1..OVERSIZED_PAGE).map { UUID.randomUUID() }.toSet())
            }
        } finally {
            closeStorage(storage)
        }
    }

    private fun createSchema(sql: co.aikar.idb.Database) {
        sql.executeUpdate("CREATE TABLE guilds(id VARCHAR(36) PRIMARY KEY, name TEXT)")
        sql.executeUpdate("CREATE TABLE members(guild_id TEXT, player_id TEXT, rank_id TEXT)")
        sql.executeUpdate("CREATE TABLE ranks(id TEXT, guild_id TEXT, priority INTEGER)")
        sql.executeUpdate("CREATE TABLE relations(guild_a TEXT, guild_b TEXT, type TEXT, status TEXT, expires_at TEXT)")
    }

    private companion object {
        const val OVERSIZED_PAGE = 37
    }
}
