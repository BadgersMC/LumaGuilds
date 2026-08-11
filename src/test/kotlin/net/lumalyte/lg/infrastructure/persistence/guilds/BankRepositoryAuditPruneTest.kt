package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.AuditAction
import net.lumalyte.lg.domain.entities.BankAudit
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/**
 * Regression: deleteAuditsOlderThan must compare timestamps chronologically.
 * Raw text comparison of Instant.toString() is wrong when fractional seconds
 * differ — "…00:00:00Z" vs "…00:00:00.001Z" (a whole-second audit is EARLIER,
 * but 'Z' sorts after '.', so text compare would retain it).
 */
class BankRepositoryAuditPruneTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var repository: BankRepositorySQLite
    private lateinit var storage: VirtualThreadSQLiteStorage

    @BeforeEach
    fun setUp() {
        storage = VirtualThreadSQLiteStorage(tempDir.toFile())
        repository = BankRepositorySQLite(storage)
    }

    @AfterEach
    fun tearDown() {
        // Release the SQLite file handle so JUnit can delete the temp dir.
        storage.connection.close()
    }

    private fun audit(guildId: UUID, id: UUID, timestamp: Instant): BankAudit {
        return BankAudit(
            id = id,
            guildId = guildId,
            actorId = UUID(0L, 0L),
            action = AuditAction.DEPOSIT,
            details = "test",
            newBalance = 100,
            timestamp = timestamp
        )
    }

    @Test
    fun `whole-second audit older than fractional cutoff IS pruned`() {
        val guildId = UUID.randomUUID()
        // Audit lands on a whole second; cutoff carries fractional seconds.
        val oldAudit = audit(guildId, UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"))
        val cutoff = Instant.parse("2026-01-01T00:00:00.500Z")

        repository.recordAudit(oldAudit)
        val pruned = repository.deleteAuditsOlderThan(guildId, cutoff)

        assertEquals(1, pruned, "chronologically-older whole-second audit must be deleted")
        assertNull(repository.getAuditForGuild(guildId).firstOrNull { it.id == oldAudit.id })
    }

    @Test
    fun `fractional-second audit just inside the cutoff is pruned`() {
        val guildId = UUID.randomUUID()
        val oldAudit = audit(guildId, UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00.250Z"))
        val cutoff = Instant.parse("2026-01-01T00:00:00.500Z")

        repository.recordAudit(oldAudit)
        val pruned = repository.deleteAuditsOlderThan(guildId, cutoff)

        assertEquals(1, pruned)
        assertNull(repository.getAuditForGuild(guildId).firstOrNull { it.id == oldAudit.id })
    }

    @Test
    fun `audit after the cutoff is retained regardless of fractional digits`() {
        val guildId = UUID.randomUUID()
        val newerAudit = audit(guildId, UUID.randomUUID(), Instant.parse("2026-01-01T00:00:01Z"))
        val cutoff = Instant.parse("2026-01-01T00:00:00.500Z")

        repository.recordAudit(newerAudit)
        val pruned = repository.deleteAuditsOlderThan(guildId, cutoff)

        assertEquals(0, pruned)
        assertEquals(1, repository.getAuditForGuild(guildId).size)
    }

    @Test
    fun `prune only affects the target guild`() {
        val guildId = UUID.randomUUID()
        val otherGuildId = UUID.randomUUID()
        val oldAudit = audit(guildId, UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"))
        val otherAudit = audit(otherGuildId, UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"))
        val cutoff = Instant.parse("2026-01-01T00:00:00.500Z")

        repository.recordAudit(oldAudit)
        repository.recordAudit(otherAudit)
        val pruned = repository.deleteAuditsOlderThan(guildId, cutoff)

        assertEquals(1, pruned)
        assertEquals(0, repository.getAuditForGuild(guildId).size)
        assertEquals(1, repository.getAuditForGuild(otherGuildId).size)
    }
}
