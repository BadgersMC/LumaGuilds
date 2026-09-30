package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.EntityType
import net.lumalyte.lg.domain.entities.ExtendedLeaderboardType
import net.lumalyte.lg.domain.entities.LeaderboardEntry
import net.lumalyte.lg.domain.entities.LeaderboardPeriod
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

private const val LARGE_TIMESTAMP = 1_800_000_000_000L

/** Verifies leaderboard rows through the real SQLite adapter. */
internal class LeaderboardRepositorySQLiteTest {
    /** SQLite REAL values and mixed-width INTEGER timestamps round-trip. */
    @Test
    fun readsRealAndIntegerValues(@TempDir tempDir: Path) {
        val storage = VirtualThreadSQLiteStorage(tempDir.toFile())
        try {
            val repository = LeaderboardRepositorySQLite(storage)
            val allTime = allTimeEntry()
            val weekly = weeklyEntry()
            assertTrue(repository.saveLeaderboardEntry(allTime))
            assertTrue(repository.saveLeaderboardEntry(weekly))

            for (reader in listOf(repository, LeaderboardRepositorySQLite(storage))) {
                assertEquals(allTime, reader.getLeaderboardEntries(allTime.leaderboardType, allTime.period, 1).single())
                assertEquals(weekly, reader.getLeaderboardEntries(weekly.leaderboardType, weekly.period, 1).single())
            }
        } finally {
            storage.connection.close()
        }
    }

    private fun allTimeEntry() = LeaderboardEntry(
        leaderboardType = ExtendedLeaderboardType.GUILD_BANK_BALANCE,
        entityId = UUID.randomUUID(),
        entityType = EntityType.GUILD,
        value = 42.5,
        rank = 1,
        lastUpdated = Instant.ofEpochMilli(LARGE_TIMESTAMP),
    )

    private fun weeklyEntry() = LeaderboardEntry(
        leaderboardType = ExtendedLeaderboardType.WEEKLY_ACTIVITY,
        entityId = UUID.randomUUID(),
        entityType = EntityType.GUILD,
        value = 0.0,
        rank = 1,
        period = LeaderboardPeriod.WEEKLY,
        periodStart = Instant.EPOCH,
        periodEnd = Instant.ofEpochMilli(LARGE_TIMESTAMP),
        lastUpdated = Instant.ofEpochMilli(LARGE_TIMESTAMP),
    )
}
