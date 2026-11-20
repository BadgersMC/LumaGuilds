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

    /** A snapshot is read back under its own type and period, with its bounds intact. */
    @Test
    fun snapshotsAreReadByTheirOwnTypeAndPeriod(@TempDir tempDir: Path) {
        val storage = VirtualThreadSQLiteStorage(tempDir.toFile())
        try {
            val repository = LeaderboardRepositorySQLite(storage)
            val start = Instant.ofEpochMilli(LARGE_TIMESTAMP - 604_800_000L)
            val end = Instant.ofEpochMilli(LARGE_TIMESTAMP)
            val level = net.lumalyte.lg.domain.entities.LeaderboardSnapshot(
                id = UUID.randomUUID(), type = net.lumalyte.lg.domain.entities.LeaderboardType.LEVEL,
                periodStart = start, periodEnd = end, data = "[]", createdAt = end, period = LeaderboardPeriod.WEEKLY,
            )
            assertTrue(repository.saveLeaderboardSnapshot(level))

            assertEquals(listOf(level), repository.getLeaderboardSnapshots(ExtendedLeaderboardType.GUILD_BANK_BALANCE, LeaderboardPeriod.WEEKLY, 5))
            assertEquals(emptyList<Any>(), repository.getLeaderboardSnapshots(ExtendedLeaderboardType.GUILD_KILLS, LeaderboardPeriod.WEEKLY, 5))
            assertEquals(emptyList<Any>(), repository.getLeaderboardSnapshots(ExtendedLeaderboardType.GUILD_LEVEL, LeaderboardPeriod.MONTHLY, 5))
        } finally {
            storage.connection.close()
        }
    }

    /** Rows saved before the bounds were stored ("{}" metadata) still load. */
    @Test
    fun oldSnapshotRowsStillLoad(@TempDir tempDir: Path) {
        val storage = VirtualThreadSQLiteStorage(tempDir.toFile())
        try {
            val repository = LeaderboardRepositorySQLite(storage)
            val id = UUID.randomUUID()
            storage.connection.executeUpdate(
                "INSERT INTO leaderboard_snapshots (id, leaderboard_type, period, snapshot_time, entries_data, metadata) VALUES (?, ?, ?, ?, ?, ?)",
                id.toString(), "KILLS", "ALL_TIME", LARGE_TIMESTAMP, "[]", "{}",
            )
            val snapshot = repository.getLeaderboardSnapshots(ExtendedLeaderboardType.PLAYER_KILLS, LeaderboardPeriod.ALL_TIME, 5).single()
            assertEquals(id, snapshot.id)
            assertEquals(Instant.ofEpochMilli(LARGE_TIMESTAMP), snapshot.periodEnd)
        } finally {
            storage.connection.close()
        }
    }

    private fun allTimeEntry(): LeaderboardEntry {
        return LeaderboardEntry(
            leaderboardType = ExtendedLeaderboardType.GUILD_BANK_BALANCE,
            entityId = UUID.randomUUID(),
            entityType = EntityType.GUILD,
            value = 42.5,
            rank = 1,
            lastUpdated = Instant.ofEpochMilli(LARGE_TIMESTAMP),
        )
    }

    private fun weeklyEntry(): LeaderboardEntry {
        return LeaderboardEntry(
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
}
