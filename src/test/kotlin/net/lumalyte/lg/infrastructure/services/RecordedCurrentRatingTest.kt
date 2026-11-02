package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.domain.values.SeasonalEloSettings
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecordedCurrentRatingTest {
    @Test
    fun `only returns an actual rating inside the active chapter window`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use {
                it.execute("CREATE TABLE chapter_lifecycle (chapter_id TEXT, chapter_name TEXT, phase TEXT, starts_at INTEGER, ends_at INTEGER)")
                it.execute("CREATE TABLE chapter_seasonal_ratings (chapter_id TEXT, guild_id TEXT, elo INTEGER, updated_at INTEGER)")
                it.execute("INSERT INTO chapter_lifecycle VALUES ('season', 'Season', 'SCHEDULED', 100, 200)")
            }
            val guild = UUID.randomUUID()
            fun rating(now: Long) = readRecordedCurrentRating(connection, false, SeasonalEloSettings(), guild, now)
            assertNull(rating(150))
            connection.prepareStatement("INSERT INTO chapter_seasonal_ratings VALUES ('season', ?, 1234, 1)").use {
                it.setString(1, guild.toString())
                it.executeUpdate()
            }
            assertNull(rating(99))
            assertEquals(1234, rating(100))
            assertEquals(1234, rating(199))
            assertNull(rating(200))
            connection.createStatement().use { it.execute("UPDATE chapter_lifecycle SET phase='FROZEN'") }
            assertNull(rating(150))
        }
    }
}
