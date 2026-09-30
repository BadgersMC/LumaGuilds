package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class ChatSettingsRepositorySQLiteTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `preload accepts mixed SQLite integer widths for rate limit timestamps`() {
        val storage = VirtualThreadSQLiteStorage(tempDir.toFile())
        try {
            val playerId = UUID.randomUUID()
            val announceTime = 1_800_000_000_000L
            storage.connection.executeUpdate(
                """CREATE TABLE chat_rate_limits (
                    player_id TEXT PRIMARY KEY,
                    last_announce_time INTEGER NOT NULL DEFAULT 0,
                    last_ping_time INTEGER NOT NULL DEFAULT 0,
                    announce_count INTEGER NOT NULL DEFAULT 0,
                    ping_count INTEGER NOT NULL DEFAULT 0
                )""".trimIndent()
            )
            storage.connection.executeUpdate(
                """INSERT INTO chat_rate_limits
                    (player_id, last_announce_time, last_ping_time, announce_count, ping_count)
                    VALUES (?, ?, 0, 1, 0)""".trimIndent(),
                playerId.toString(), announceTime
            )

            val repository = ChatSettingsRepositorySQLite(storage)
            val rateLimit = repository.getRateLimit(playerId)
            assertEquals(announceTime, rateLimit.lastAnnounceTime)
            assertEquals(0L, rateLimit.lastPingTime)
            assertEquals(1, rateLimit.announceCount)
        } finally {
            storage.connection.close()
        }
    }
}
