package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.EmojiPermissionGrant
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmojiGrantRepositorySQLiteTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: EmojiGrantRepositorySQLite

    private val playerId = UUID.randomUUID()
    private val otherPlayerId = UUID.randomUUID()
    private val guildId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        storage = VirtualThreadSQLiteStorage(tempDir.toFile())
        repository = EmojiGrantRepositorySQLite(storage)
    }

    @AfterEach
    fun tearDown() {
        storage.connection.close()
    }

    @Test
    fun `owned grant survives repository restart`() {
        val grant = EmojiPermissionGrant(playerId, guildId, "enthusia.emoji.badger")
        assertTrue(repository.upsert(grant))

        val secondStorage = VirtualThreadSQLiteStorage(tempDir.toFile())
        try {
            assertEquals(
                grant,
                EmojiGrantRepositorySQLite(secondStorage).getForPlayerAndGuild(playerId, guildId),
            )
        } finally {
            secondStorage.connection.close()
        }
    }

    @Test
    fun `upsert replaces one memberships permission`() {
        repository.upsert(EmojiPermissionGrant(playerId, guildId, "enthusia.emoji.old"))
        repository.upsert(EmojiPermissionGrant(playerId, guildId, "enthusia.emoji.new"))

        assertEquals("enthusia.emoji.new", repository.getForPlayerAndGuild(playerId, guildId)?.permission)
        assertEquals(1, repository.getAll().size)
    }

    @Test
    fun `delete preserves other guild members`() {
        repository.upsert(EmojiPermissionGrant(playerId, guildId, "enthusia.emoji.badger"))
        repository.upsert(EmojiPermissionGrant(otherPlayerId, guildId, "enthusia.emoji.badger"))

        assertTrue(repository.delete(playerId, guildId))

        assertNull(repository.getForPlayerAndGuild(playerId, guildId))
        assertNotNull(repository.getForPlayerAndGuild(otherPlayerId, guildId))
    }

    @Test
    fun `guild lookup returns only that guilds owned grants`() {
        val otherGuildId = UUID.randomUUID()
        repository.upsert(EmojiPermissionGrant(playerId, guildId, "enthusia.emoji.badger"))
        repository.upsert(EmojiPermissionGrant(playerId, otherGuildId, "enthusia.emoji.dragon"))

        assertEquals(
            listOf(EmojiPermissionGrant(playerId, guildId, "enthusia.emoji.badger")),
            repository.getForGuild(guildId),
        )
    }
}
