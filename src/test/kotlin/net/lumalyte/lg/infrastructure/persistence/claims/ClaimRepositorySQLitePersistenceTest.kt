package net.lumalyte.lg.infrastructure.persistence.claims

import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.values.Position3D
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClaimRepositorySQLitePersistenceTest {
    @TempDir lateinit var directory: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: ClaimRepositorySQLite

    @BeforeEach
    fun setup() {
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        repository = ClaimRepositorySQLite(storage)
    }

    @AfterEach
    fun cleanup() {
        storage.connection.close()
    }

    @Test
    fun `failed durable update does not mutate cached claim`() {
        val original = claim()
        assertTrue(repository.add(original))
        storage.connection.executeUpdate(
            "DELETE FROM claims WHERE id = ?",
            original.id,
        )

        val changed = original.copy(name = "Changed")
        assertFalse(repository.update(changed))
        assertEquals(original, repository.getById(original.id))
    }

    @Test
    fun `conditional owner update refuses stale ownership snapshot`() {
        val original = claim()
        val durableOwner = UUID.randomUUID()
        assertTrue(repository.add(original))
        storage.connection.executeUpdate(
            "UPDATE claims SET owner_id = ? WHERE id = ?",
            durableOwner,
            original.id,
        )

        val staleTransfer = original.copy(playerId = UUID.randomUUID())
        assertFalse(repository.updateIfOwnedBy(staleTransfer, original.playerId))
        assertEquals(original, repository.getById(original.id))
    }

    private fun claim() = Claim(
        worldId = UUID.randomUUID(),
        playerId = UUID.randomUUID(),
        position = Position3D(0, 64, 0),
        name = "Original",
    )
}
