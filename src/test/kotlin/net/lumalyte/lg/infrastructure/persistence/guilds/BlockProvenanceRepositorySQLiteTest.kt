package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.values.BlockPosition
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class BlockProvenanceRepositorySQLiteTest {
    @TempDir lateinit var tempDir: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: BlockProvenanceRepositorySQLite

    @BeforeEach fun setUp() {
        storage = VirtualThreadSQLiteStorage(tempDir.toFile())
        storage.connection.executeUpdate("""
            CREATE TABLE quest_player_placed_blocks (
                world_id TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL,
                PRIMARY KEY (world_id, x, y, z)
            )
        """.trimIndent())
        repository = BlockProvenanceRepositorySQLite(storage)
    }

    @AfterEach fun tearDown() { storage.connection.close() }

    @Test
    fun `placed block remains ineligible after repository recreation and is removed on break`() {
        val position = BlockPosition(UUID.randomUUID(), 10, 64, -5)
        repository.recordPlayerPlaced(position)

        val secondStorage = VirtualThreadSQLiteStorage(tempDir.toFile())
        try {
            val second = BlockProvenanceRepositorySQLite(secondStorage)
            assertTrue(second.wasPlayerPlaced(position))
            assertTrue(second.remove(position))
            assertFalse(second.wasPlayerPlaced(position))
        } finally {
            secondStorage.connection.close()
        }
    }

    @Test
    fun `piston movement transfers provenance to destination`() {
        val world = UUID.randomUUID()
        val source = BlockPosition(world, 1, 2, 3)
        val destination = BlockPosition(world, 2, 2, 3)
        repository.recordPlayerPlaced(source)

        repository.move(source, destination)

        assertFalse(repository.wasPlayerPlaced(source))
        assertTrue(repository.wasPlayerPlaced(destination))
    }
}
