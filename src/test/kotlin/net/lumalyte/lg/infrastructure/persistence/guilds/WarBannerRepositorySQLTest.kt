package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.WarBannerState
import net.lumalyte.lg.infrastructure.persistence.migrations.WarBannerSchema
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WarBannerRepositorySQLTest {
    @TempDir lateinit var directory: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: WarBannerRepositorySQL

    @BeforeEach
    fun setup() {
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        storage.connection.connection.use { WarBannerSchema.create(it, mariaDb = false) }
        repository = WarBannerRepositorySQL(storage)
    }

    @AfterEach
    fun cleanup() {
        storage.connection.close()
    }

    @Test
    fun `placement and cooldown state survive repository recreation`() {
        val original = state()
        assertTrue(repository.savePlacement(original))

        storage.connection.close()
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        repository = WarBannerRepositorySQL(storage)

        assertEquals(original, repository.get(original.guildId))
        assertEquals(
            original,
            repository.getActiveAt(original.worldId, original.x, original.y, original.z),
        )
        assertTrue(repository.deactivate(original.guildId, original.bannerId))
        assertFalse(requireNotNull(repository.get(original.guildId)).active)
        assertNull(repository.getActiveAt(original.worldId, original.x, original.y, original.z))
    }

    @Test
    fun `expired active query returns only due banners`() {
        val original = state(expiresAt = 500)
        assertTrue(repository.savePlacement(original))

        assertTrue(repository.expiredActive(499).isEmpty())
        assertEquals(listOf(original), repository.expiredActive(500))
    }

    @Test
    fun `delete removes failed first placement state`() {
        val original = state()
        assertTrue(repository.savePlacement(original))
        assertTrue(repository.delete(original.guildId, original.bannerId))
        assertNull(repository.get(original.guildId))
    }

    private fun state(expiresAt: Long = 1_000): WarBannerState =
        WarBannerState(
            guildId = UUID.randomUUID(),
            bannerId = UUID.randomUUID(),
            worldId = UUID.randomUUID(),
            x = 10,
            y = 64,
            z = -20,
            placedBy = UUID.randomUUID(),
            transactionId = UUID.randomUUID(),
            placedAt = 100,
            expiresAt = expiresAt,
            cooldownUntil = 1_500,
            active = true,
        )
}
