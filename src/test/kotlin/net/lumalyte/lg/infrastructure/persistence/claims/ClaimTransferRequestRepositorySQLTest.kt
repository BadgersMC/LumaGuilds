package net.lumalyte.lg.infrastructure.persistence.claims

import net.lumalyte.lg.infrastructure.persistence.migrations.ClaimTransferRequestSchema
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClaimTransferRequestRepositorySQLTest {
    @TempDir lateinit var directory: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: ClaimTransferRequestRepositorySQL

    @BeforeEach
    fun setup() {
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        createSchema()
        repository = ClaimTransferRequestRepositorySQL(storage)
    }

    @AfterEach
    fun cleanup() {
        storage.connection.close()
    }

    @Test
    fun `transfer request survives storage restart`() {
        val claimId = UUID.randomUUID()
        val playerId = UUID.randomUUID()
        val now = 1_800_000_000L
        insertClaim(claimId)

        assertTrue(repository.offer(claimId, playerId, now + 300))
        assertTrue(repository.hasActive(claimId, playerId, now))

        storage.connection.close()
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        repository = ClaimTransferRequestRepositorySQL(storage)

        assertTrue(repository.hasActive(claimId, playerId, now))
        assertTrue(repository.withdraw(claimId, playerId))
        assertFalse(repository.hasActive(claimId, playerId, now))
    }

    @Test
    fun `expired request is removed when checked`() {
        val claimId = UUID.randomUUID()
        val playerId = UUID.randomUUID()
        insertClaim(claimId)
        assertTrue(repository.offer(claimId, playerId, 100))

        assertFalse(repository.hasActive(claimId, playerId, 101))
        assertFalse(repository.withdraw(claimId, playerId))
    }

    private fun createSchema() {
        storage.connection.connection.use { connection ->
            connection.createStatement().use {
                it.execute("CREATE TABLE IF NOT EXISTS claims (id TEXT PRIMARY KEY)")
            }
            ClaimTransferRequestSchema.create(connection, mariaDb = false)
        }
    }

    private fun insertClaim(claimId: UUID) {
        storage.connection.executeUpdate(
            "INSERT OR IGNORE INTO claims (id) VALUES (?)",
            claimId.toString(),
        )
    }
}
