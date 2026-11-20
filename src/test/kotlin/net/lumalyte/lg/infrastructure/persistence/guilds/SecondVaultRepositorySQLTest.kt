package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.persistence.SecondVaultSpot
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REQ-243, REQ-244, REQ-246: the second vault's spot and items persist apart from the main vault. */
class SecondVaultRepositorySQLTest : RewardSqlTestFixture() {
    private val guild = UUID.randomUUID()
    private val spot = SecondVaultSpot(UUID.randomUUID(), 812, 64, -1240)

    @Test
    fun `one second vault per guild, and its items survive reopen`() {
        val storage = openStorage()
        val repository = SecondVaultRepositorySQL(storage)
        assertTrue(repository.place(guild, spot, Instant.EPOCH))
        assertFalse(repository.place(guild, spot.copy(x = 0), Instant.EPOCH))
        repository.saveItems(guild, mapOf(0 to "a", 5 to "b"))
        repository.saveItems(guild, mapOf(5 to "c", 8 to "d"))
        closeStorage(storage)

        val reopened = SecondVaultRepositorySQL(openStorage())
        assertEquals(mapOf(guild to spot), reopened.spots())
        assertEquals(mapOf(5 to "c", 8 to "d"), reopened.items(guild))
    }

    @Test
    fun `removing the chest keeps items, disbanding removes everything`() {
        val repository = SecondVaultRepositorySQL(openStorage())
        repository.place(guild, spot, Instant.EPOCH)
        repository.saveItems(guild, mapOf(1 to "a"))
        repository.removeSpot(guild)
        assertEquals(emptyMap(), repository.spots())
        assertEquals(mapOf(1 to "a"), repository.items(guild))
        assertTrue(repository.place(guild, spot, Instant.EPOCH))
        repository.deleteAll(guild)
        assertEquals(emptyMap(), repository.spots())
        assertEquals(emptyMap(), repository.items(guild))
    }
}
