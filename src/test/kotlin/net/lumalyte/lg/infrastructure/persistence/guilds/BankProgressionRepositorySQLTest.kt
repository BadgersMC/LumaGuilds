package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.values.PeriodWindow
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

class BankProgressionRepositorySQLTest {
    @TempDir lateinit var tempDir: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: BankProgressionRepositorySQL
    private val guildId = UUID.randomUUID()
    private val day = PeriodWindow(
        Instant.parse("2026-08-28T00:00:00Z"),
        Instant.parse("2026-08-29T00:00:00Z"),
    )

    @BeforeEach
    fun setUp() {
        storage = VirtualThreadSQLiteStorage(tempDir.toFile())
        repository = BankProgressionRepositorySQL(storage)
    }

    @AfterEach
    fun tearDown() = storage.connection.close(5, TimeUnit.SECONDS)

    @Test
    fun `withdraw and redeposit cannot reserve bank units twice`() {
        assertEquals(100, repository.reserveNetNewUnits(guildId, 10_000, 100, day))
        assertEquals(0, repository.reserveNetNewUnits(guildId, 0, 100, day))
        assertEquals(0, repository.reserveNetNewUnits(guildId, 10_000, 100, day))
        assertEquals(10, repository.reserveNetNewUnits(guildId, 11_000, 100, day))
    }

    @Test
    fun `a new daily period starts from zero`() {
        assertEquals(100, repository.reserveNetNewUnits(guildId, 10_000, 100, day))
        val tomorrow = PeriodWindow(day.endExclusive, day.endExclusive.plusSeconds(86_400))
        assertEquals(100, repository.reserveNetNewUnits(guildId, 10_000, 100, tomorrow))
    }
}
