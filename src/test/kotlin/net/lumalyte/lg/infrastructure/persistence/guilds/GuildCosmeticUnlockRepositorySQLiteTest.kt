package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.GuildCosmeticUnlock
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** REQ-121: durable, idempotent guild cosmetic ownership. */
class GuildCosmeticUnlockRepositorySQLiteTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: GuildCosmeticUnlockRepositorySQLite

    private val guildId = UUID.randomUUID()
    private val unlockedAt = Instant.parse("2026-10-20T12:00:00Z")

    private fun unlock(guild: UUID = guildId, key: String = "HALLOWEEN", name: String = "Halloween '26") =
        GuildCosmeticUnlock(guild, "MENU_THEME", key, name, "event:halloween-2026/goal:haunted-hall", unlockedAt)

    @BeforeEach
    fun setUp() {
        storage = VirtualThreadSQLiteStorage(tempDir.toFile())
        repository = GuildCosmeticUnlockRepositorySQLite(storage)
    }

    @AfterEach
    fun tearDown() {
        storage.connection.close()
    }

    @Test
    fun `unlock survives repository restart`() {
        assertTrue(repository.saveIfAbsent(unlock()))

        val secondStorage = VirtualThreadSQLiteStorage(tempDir.toFile())
        try {
            assertEquals(unlock(), GuildCosmeticUnlockRepositorySQLite(secondStorage).get(guildId, "MENU_THEME", "HALLOWEEN"))
        } finally {
            secondStorage.connection.close()
        }
    }

    @Test
    fun `saving an owned cosmetic again keeps the original record`() {
        assertTrue(repository.saveIfAbsent(unlock(name = "Halloween '26")))
        assertTrue(repository.saveIfAbsent(unlock(name = "Renamed")))

        assertEquals("Halloween '26", repository.get(guildId, "MENU_THEME", "HALLOWEEN")?.displayName)
        assertEquals(1, repository.getForGuild(guildId).size)
    }

    @Test
    fun `delete is idempotent and only removes one cosmetic`() {
        repository.saveIfAbsent(unlock(key = "HALLOWEEN"))
        repository.saveIfAbsent(unlock(key = "CHRISTMAS"))

        assertTrue(repository.delete(guildId, "MENU_THEME", "HALLOWEEN"))
        assertTrue(repository.delete(guildId, "MENU_THEME", "HALLOWEEN"))

        assertNull(repository.get(guildId, "MENU_THEME", "HALLOWEEN"))
        assertEquals(listOf("CHRISTMAS"), repository.getForGuild(guildId).map { it.key })
    }

    @Test
    fun `guild lookup is isolated per guild`() {
        val otherGuild = UUID.randomUUID()
        repository.saveIfAbsent(unlock())
        repository.saveIfAbsent(unlock(guild = otherGuild, key = "CHRISTMAS"))

        assertEquals(listOf("HALLOWEEN"), repository.getForGuild(guildId).map { it.key })
        assertEquals(listOf("CHRISTMAS"), repository.getForGuild(otherGuild).map { it.key })
    }
}
