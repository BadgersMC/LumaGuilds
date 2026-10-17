package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.SpawnBannerCategory
import net.lumalyte.lg.domain.entities.SpawnBannerState
import net.lumalyte.lg.infrastructure.persistence.migrations.SpawnBannerSchema
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class SpawnBannerRepositorySQLTest {
    @TempDir lateinit var tempDir: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: SpawnBannerRepositorySQL

    @BeforeEach
    fun setUp() {
        storage = VirtualThreadSQLiteStorage(tempDir.toFile())
        storage.connection.getConnection().use { SpawnBannerSchema.create(it, mariaDb = false) }
        repository = SpawnBannerRepositorySQL(storage)
    }

    @AfterEach
    fun tearDown() {
        storage.connection.close()
    }

    @Test
    fun `save update list and delete preserve rank category and location`() {
        val world = UUID.randomUUID()
        val first = state(world, 10, 70, -5, 1, SpawnBannerCategory.LEVEL)
        val second = state(world, 12, 70, -5, 2, SpawnBannerCategory.KILLS)

        assertTrue(repository.save(first))
        assertTrue(repository.save(second))
        assertEquals(first, repository.getAt(world, 10, 70, -5))
        assertEquals(2, repository.getAll().size)

        val replacement = first.copy(
            bannerId = UUID.randomUUID(),
            rank = 3,
            category = SpawnBannerCategory.WEALTH,
        )
        assertTrue(repository.save(replacement))
        assertEquals(replacement, repository.getAt(world, 10, 70, -5))
        assertEquals(2, repository.getAll().size)

        assertTrue(repository.deleteAt(world, 10, 70, -5))
        assertNull(repository.getAt(world, 10, 70, -5))
    }

    private fun state(
        world: UUID,
        x: Int,
        y: Int,
        z: Int,
        rank: Int,
        category: SpawnBannerCategory,
    ) = SpawnBannerState(
        bannerId = UUID.randomUUID(),
        worldId = world,
        x = x,
        y = y,
        z = z,
        rank = rank,
        category = category,
        createdAt = 1_790_000_000_000L + rank,
    )
}
