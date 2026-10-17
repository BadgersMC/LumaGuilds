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
import java.sql.DriverManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import net.lumalyte.lg.infrastructure.listeners.BlockProvenanceOperationQueue
import kotlin.test.assertEquals

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
    fun `piston queues behind SQLite writer without blocking submitting thread`() {
        val world = UUID.randomUUID()
        val source = BlockPosition(world, 1, 2, 3)
        val destination = BlockPosition(world, 2, 2, 3)
        repository.recordPlayerPlaced(source)
        val executor = Executors.newSingleThreadExecutor()
        try {
            DriverManager.getConnection("jdbc:sqlite:${tempDir.resolve("lumaguilds.db")}").use { writer ->
                writer.autoCommit = false
                writer.createStatement().use {
                    it.executeUpdate("UPDATE quest_player_placed_blocks SET x = x WHERE 1 = 0")
                }
                val queue = BlockProvenanceOperationQueue(executor)
                val caller = Thread.currentThread()
                val moved = queue.submit {
                    assertFalse(Thread.currentThread() === caller)
                    repository.moveAll(listOf(source to destination))
                }
                assertFalse(moved.isDone)
                writer.commit()
                moved.get(5, TimeUnit.SECONDS)
            }
            assertFalse(repository.wasPlayerPlaced(source))
            assertTrue(repository.wasPlayerPlaced(destination))
        } finally { executor.shutdownNow() }
    }

    @Test
    fun `burst of queued piston moves and break reads preserves provenance`() {
        val executor = Executors.newFixedThreadPool(4)
        try {
            val queue = BlockProvenanceOperationQueue(executor)
            val world = UUID.randomUUID()
            val operations = (1..250).flatMap { index ->
                val source = BlockPosition(world, index * 3, 64, 0)
                val destination = BlockPosition(world, index * 3 + 1, 64, 0)
                listOf(
                    queue.submit { repository.recordPlayerPlaced(source) },
                    queue.submit { repository.moveAll(listOf(source to destination)) },
                    queue.submit {
                        assertTrue(repository.wasPlayerPlaced(destination))
                        repository.remove(destination)
                    },
                )
            }
            operations.forEach { it.get(15, TimeUnit.SECONDS) }
            assertEquals(0, storage.connection.getFirstRow("SELECT COUNT(*) AS n FROM quest_player_placed_blocks")!!.getInt("n"))
        } finally { executor.shutdownNow() }
    }

    @Test
    fun `overlapping piston chain preserves each marker without ordering assumptions`() {
        val world = UUID.randomUUID()
        val a = BlockPosition(world, 1, 2, 3)
        val b = BlockPosition(world, 2, 2, 3)
        val c = BlockPosition(world, 3, 2, 3)
        repository.recordPlayerPlaced(a)
        repository.recordPlayerPlaced(b)
        repository.moveAll(listOf(a to b, b to c))
        assertFalse(repository.wasPlayerPlaced(a))
        assertTrue(repository.wasPlayerPlaced(b))
        assertTrue(repository.wasPlayerPlaced(c))
    }

    @Test
    fun `natural neighbour does not inherit marker from earlier move in same batch`() {
        val world = UUID.randomUUID()
        val a = BlockPosition(world, 1, 2, 3)
        val b = BlockPosition(world, 2, 2, 3)
        val c = BlockPosition(world, 3, 2, 3)
        repository.recordPlayerPlaced(a)
        repository.moveAll(listOf(a to b, b to c))
        assertFalse(repository.wasPlayerPlaced(a))
        assertTrue(repository.wasPlayerPlaced(b))
        assertFalse(repository.wasPlayerPlaced(c))
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
