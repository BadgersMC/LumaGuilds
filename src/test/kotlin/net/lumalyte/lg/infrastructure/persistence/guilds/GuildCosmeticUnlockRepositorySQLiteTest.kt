// Explicit fixture numbers document persisted coordinates, icon dimensions and approved boundaries.
@file:Suppress("MagicNumber")

package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.domain.entities.GuildCosmeticUnlock
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** REQ-121: durable, idempotent guild cosmetic ownership. */
internal class GuildCosmeticUnlockRepositorySQLiteTest : RewardSqlTestFixture() {
    private var storage: Storage<Database> by kotlin.properties.Delegates.notNull()
    private var repository: GuildCosmeticUnlockRepositorySQLite by kotlin.properties.Delegates.notNull()

    private val guildId = UUID.randomUUID()
    private val unlockedAt = Instant.parse("2026-10-20T12:00:00Z")

    private fun unlock(guild: UUID = guildId, key: String = TEST_HALLOWEEN, name: String = TEST_HOLIDAY_NAME) =
        GuildCosmeticUnlock(guild, TEST_MENU_THEME, key, name, "event:halloween-2026/goal:haunted-hall", unlockedAt)

    /** Initialize the disposable fixture and service dependencies. */
    @BeforeEach
    fun setUp() {
        storage = openStorage()
        repository = GuildCosmeticUnlockRepositorySQLite(storage)
    }

    /** Unlock survives repository restart. */
    @DisplayName("unlock survives repository restart")
    @Test
    fun scenario1() {
        assertTrue(repository.saveIfAbsent(unlock()))

        val secondStorage = openStorage()
        try {
            assertEquals(
                unlock(),
                GuildCosmeticUnlockRepositorySQLite(secondStorage).get(guildId, TEST_MENU_THEME, TEST_HALLOWEEN),
            )
        } finally {
            closeStorage(secondStorage)
        }
    }

    /** Saving an owned cosmetic again keeps the original record. */
    @DisplayName("saving an owned cosmetic again keeps the original record")
    @Test
    fun scenario2() {
        assertTrue(repository.saveIfAbsent(unlock(name = TEST_HOLIDAY_NAME)))
        assertTrue(repository.saveIfAbsent(unlock(name = "Renamed")))

        assertEquals(TEST_HOLIDAY_NAME, repository.get(guildId, TEST_MENU_THEME, TEST_HALLOWEEN)?.displayName)
        assertEquals(1, repository.getForGuild(guildId).size)
    }

    /** Delete is idempotent and only removes one cosmetic. */
    @DisplayName("delete is idempotent and only removes one cosmetic")
    @Test
    fun scenario3() {
        repository.saveIfAbsent(unlock(key = TEST_HALLOWEEN))
        repository.saveIfAbsent(unlock(key = TEST_CHRISTMAS))

        assertTrue(repository.delete(guildId, TEST_MENU_THEME, TEST_HALLOWEEN))
        assertTrue(repository.delete(guildId, TEST_MENU_THEME, TEST_HALLOWEEN))

        assertNull(repository.get(guildId, TEST_MENU_THEME, TEST_HALLOWEEN))
        assertEquals(listOf(TEST_CHRISTMAS), repository.getForGuild(guildId).map { it.key })
    }

    /** Guild lookup is isolated per guild. */
    @DisplayName("guild lookup is isolated per guild")
    @Test
    fun scenario4() {
        val otherGuild = UUID.randomUUID()
        repository.saveIfAbsent(unlock())
        repository.saveIfAbsent(unlock(guild = otherGuild, key = TEST_CHRISTMAS))

        assertEquals(listOf(TEST_HALLOWEEN), repository.getForGuild(guildId).map { it.key })
        assertEquals(listOf(TEST_CHRISTMAS), repository.getForGuild(otherGuild).map { it.key })
    }
}

private const val TEST_MENU_THEME = "MENU_THEME"

private const val TEST_HALLOWEEN = "HALLOWEEN"

private const val TEST_HOLIDAY_NAME = "Halloween '26"

private const val TEST_CHRISTMAS = "CHRISTMAS"
