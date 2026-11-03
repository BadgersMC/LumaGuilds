package net.lumalyte.lg.infrastructure.vault

import io.mockk.mockk
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.infrastructure.persistence.guilds.ProgressionRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Real SQLite proof that the hot vault guard bypasses stale progression cache. */
class VaultCommittedLevelSqliteTest {
    @TempDir lateinit var directory: Path

    @Test fun `committed level read sees prestige SQL reset despite cached level one hundred`() {
        val storage = SQLiteStorage(directory.toFile())
        try {
            val repository = ProgressionRepositorySQLite(storage, mockk<ConfigService>(relaxed = true))
            val id = UUID.randomUUID()
            storage.connection.executeUpdate(
                """
                    INSERT INTO guild_progression
                    (guild_id, total_experience, current_level, experience_this_level,
                     experience_for_next_level, last_level_up, total_level_ups,
                     unlocked_perks, created_at, last_updated)
                    VALUES (?, 0, 100, 0, 1000, NULL, 0, '[]', 0, 0)
                """.trimIndent(), id.toString(),
            )
            assertEquals(100, repository.refreshGuildProgression(id)?.currentLevel)

            // Simulates PrestigeRepositorySQL committing level 1 directly to the DB.
            storage.connection.executeUpdate(
                "UPDATE guild_progression SET current_level = 1 WHERE guild_id = ?",
                id.toString(),
            )
            assertEquals(100, repository.getGuildProgression(id)?.currentLevel, "Baseline cache is stale")
            assertEquals(1, repository.readCommittedLevel(id), "Vault guard must consult committed SQL")
        } finally {
            storage.connection.close(5, TimeUnit.SECONDS)
        }
    }
}
