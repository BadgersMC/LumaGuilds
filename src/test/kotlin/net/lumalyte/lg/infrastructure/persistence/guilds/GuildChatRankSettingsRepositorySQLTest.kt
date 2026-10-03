package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import net.lumalyte.lg.domain.entities.Rank
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*

class GuildChatRankSettingsRepositorySQLTest {
    @Test
    fun restartPreservesEachGuildSettingAndFailedWriteKeepsCache(@TempDir folder: Path) {
        val storage = VirtualThreadSQLiteStorage(folder.toFile())
        try {
            storage.connection.executeUpdate("CREATE TABLE guilds (id TEXT PRIMARY KEY)")
            val first = UUID.randomUUID()
            val second = UUID.randomUUID()
            listOf(first, second).forEach {
                storage.connection.executeUpdate("INSERT INTO guilds(id) VALUES (?)", it.toString())
            }
            val repository = GuildChatRankSettingsRepositorySQL(storage)
            assertTrue(repository.ranksVisible(first))
            assertTrue(repository.setRanksVisible(first, false))
            assertTrue(repository.ranksVisible(second))

            val reloaded = GuildChatRankSettingsRepositorySQL(storage)
            assertFalse(reloaded.ranksVisible(first))
            assertTrue(reloaded.ranksVisible(second))
            storage.connection.executeUpdate("DROP TABLE guild_chat_rank_settings")
            assertFalse(reloaded.setRanksVisible(first, true))
            assertFalse(reloaded.ranksVisible(first))
        } finally {
            storage.connection.close()
        }
    }
    @Test
    fun formattedRankPersistsAndUsesVisibleNameIdentity(@TempDir folder: Path) {
        val storage = VirtualThreadSQLiteStorage(folder.toFile())
        try {
            val guild = UUID.randomUUID()
            storage.connection.executeUpdate("CREATE TABLE guilds (id TEXT PRIMARY KEY)")
            storage.connection.executeUpdate("INSERT INTO guilds(id) VALUES (?)", guild.toString())
            val repository = RankRepositorySQLite(storage)
            val rank = Rank(UUID.randomUUID(), guild, "&a&l" + "A".repeat(24))
            assertTrue(repository.add(rank))
            val reloaded = RankRepositorySQLite(storage)
            assertEquals(rank.name, reloaded.getById(rank.id)?.name)
            assertEquals(rank.id, reloaded.getByName(guild, "&b" + "a".repeat(24))?.id)
            assertTrue(reloaded.isNameTaken(guild, "A".repeat(24)))
        } finally {
            storage.connection.close()
        }
    }
}
