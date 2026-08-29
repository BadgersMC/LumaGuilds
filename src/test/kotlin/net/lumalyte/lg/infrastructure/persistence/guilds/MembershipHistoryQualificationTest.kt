package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.DepartureReason
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

class MembershipHistoryQualificationTest {
    @TempDir lateinit var tempDir: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: MembershipHistoryRepositorySQLite

    @BeforeEach
    fun setUp() {
        storage = VirtualThreadSQLiteStorage(tempDir.toFile())
        repository = MembershipHistoryRepositorySQLite(storage)
    }

    @AfterEach
    fun tearDown() = storage.connection.close(5, TimeUnit.SECONDS)

    @Test
    fun `open retained stint is returned and marked exactly once`() {
        val playerId = UUID.randomUUID()
        val guildId = UUID.randomUUID()
        assertTrue(repository.openStint(playerId, guildId))

        val candidates = repository.getQualifiedUnawarded(Instant.now().plusSeconds(1))
        assertEquals(1, candidates.size)
        assertTrue(repository.markRecruitXpAwarded(candidates.single().id, Instant.now()))
        assertNotNull(repository.getByPlayer(playerId).single().recruitXpAwardedAt)
        assertEquals(emptyList<Any>(), repository.getQualifiedUnawarded(Instant.now().plusSeconds(1)))
    }

    @Test
    fun `departed stint never qualifies`() {
        val playerId = UUID.randomUUID()
        val guildId = UUID.randomUUID()
        repository.openStint(playerId, guildId)
        repository.closeStint(playerId, guildId, DepartureReason.LEFT)

        assertEquals(emptyList<Any>(), repository.getQualifiedUnawarded(Instant.now().plusSeconds(1)))
    }
}
