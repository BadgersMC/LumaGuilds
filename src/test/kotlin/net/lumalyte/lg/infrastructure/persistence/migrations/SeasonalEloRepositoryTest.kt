package net.lumalyte.lg.infrastructure.persistence.migrations

import net.lumalyte.lg.domain.values.SeasonalEloSettings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class SeasonalEloRepositoryTest {
    @TempDir lateinit var tempDir: Path
    private lateinit var connection: Connection
    private val a = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val b = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @BeforeEach fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve("elo.db"))
        connection.createStatement().use { s ->
            s.execute("CREATE TABLE guilds (id TEXT PRIMARY KEY, name TEXT NOT NULL)")
            s.execute("CREATE TABLE guild_progression (guild_id TEXT PRIMARY KEY,current_level INTEGER NOT NULL,total_experience INTEGER NOT NULL)")
            s.execute("INSERT INTO guilds VALUES ('$a','Alpha')")
            s.execute("INSERT INTO guilds VALUES ('$b','Beta')")
            s.execute("INSERT INTO guild_progression VALUES ('$a',100,5446893)")
            s.execute("INSERT INTO guild_progression VALUES ('$b',100,5446893)")
        }
        ChapterLifecycleSchema.create(connection, false)
        SeasonalEloSchema.create(connection, false)
        connection.createStatement().use { s ->
            s.execute("INSERT INTO chapter_lifecycle (chapter_id,chapter_name,phase,starts_at,ends_at,updated_at,version) VALUES ('c2','Chapter 2','SCHEDULED',1,9999999999999,1,0)")
            s.execute("INSERT INTO chapter_seasonal_ratings VALUES ('c2','$a',1000,1)")
            s.execute("INSERT INTO chapter_seasonal_ratings VALUES ('c2','$b',1000,1)")
        }
    }

    @AfterEach fun tearDown() = connection.close()

    @Test fun ratedWarUpdatesBothRatingsAndPairGuardAtomically() {
        val repo = repo()
        val result = repo.rate(UUID.randomUUID(), "c2", a, b, 1.0, 0.0, 1_000_000)
        assertTrue(result is SeasonalWarRatingResult.Rated)
        assertEquals(1020, rating(a))
        assertEquals(1000, rating(b))
        assertEquals(1, count("chapter_rated_pair_guards"))
        assertEquals(1, count("chapter_rated_war_results"))
    }

    @Test fun unorderedRematchInsideWindowIsPlayableButUnrated() {
        val repo = repo()
        repo.rate(UUID.randomUUID(), "c2", a, b, 1.0, 0.0, 1_000_000)
        val result = repo.rate(UUID.randomUUID(), "c2", b, a, 1.0, 0.0, 1_000_001)
        assertTrue(result is SeasonalWarRatingResult.RematchGuarded)
        assertEquals(1, count("chapter_rated_war_results"))
    }

    @Test fun sameWarReplayDoesNotApplyTwice() {
        val war = UUID.randomUUID()
        val repo = repo()
        val first = repo.rate(war, "c2", a, b, 1.0, 0.0, 1_000_000)
        val replay = repo.rate(war, "c2", a, b, 1.0, 0.0, 1_000_001)
        assertTrue(first is SeasonalWarRatingResult.Rated)
        assertTrue(replay is SeasonalWarRatingResult.Replayed)
        assertEquals(1020, rating(a))
    }

    @Test fun ratedResultDoesNotChangeCurrentRunProgression() {
        val beforeA = progression(a)
        val beforeB = progression(b)

        repo().rate(UUID.randomUUID(), "c2", a, b, 1.0, 0.0, 1_000_000)

        assertEquals(beforeA, progression(a))
        assertEquals(beforeB, progression(b))
    }

    @Test fun rematchAtWindowBoundaryRatesAgain() {
        val settings = SeasonalEloSettings(rematchWindowMillis = 7L * 24L * 60L * 60L * 1000L)
        val repo = SeasonalEloRepositorySQL(connection, false, settings)
        repo.rate(UUID.randomUUID(), "c2", a, b, 1.0, 0.0, 1_000_000)
        val result = repo.rate(
            UUID.randomUUID(), "c2", b, a, 0.0, 1.0,
            1_000_000 + settings.rematchWindowMillis,
        )
        assertTrue(result is SeasonalWarRatingResult.Rated)
        assertEquals(2, count("chapter_rated_war_results"))
    }

    @Test fun invalidNonComplementaryScoresAreRejectedBeforeMutation() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            repo().rate(UUID.randomUUID(), "c2", a, b, 1.0, 1.0, 1_000_000)
        }
        assertEquals(0, count("chapter_rated_war_results"))
        assertEquals(1000, rating(a))
        assertEquals(1000, rating(b))
        assertEquals(2, count("chapter_seasonal_ratings"))
    }

    @Test fun oldChapterCannotReceiveRatingAfterItLeavesScheduledPhase() {
        connection.createStatement().use {
            it.execute("UPDATE chapter_lifecycle SET phase='COMPLETE' WHERE chapter_id='c2'")
            it.execute("INSERT INTO chapter_lifecycle (chapter_id,chapter_name,phase,starts_at,ends_at,updated_at,version) VALUES ('c3','Chapter 3','SCHEDULED',2,9999999999999,2,0)")
        }
        val result = repo().rate(UUID.randomUUID(), "c2", a, b, 1.0, 0.0, 1_000_000)
        assertTrue(result is SeasonalWarRatingResult.Frozen)
        assertEquals(0, count("chapter_rated_war_results"))
    }

    @Test fun preLevel100AndFrozenChapterDoNotRate() {
        connection.createStatement().use { it.execute("UPDATE guild_progression SET current_level=99 WHERE guild_id='$a'") }
        assertTrue(repo().rate(UUID.randomUUID(),"c2",a,b,1.0,0.0,1000) is SeasonalWarRatingResult.Ineligible)
        connection.createStatement().use {
            it.execute("UPDATE guild_progression SET current_level=100 WHERE guild_id='$a'")
            it.execute("UPDATE chapter_lifecycle SET phase='FROZEN' WHERE chapter_id='c2'")
        }
        assertTrue(repo().rate(UUID.randomUUID(),"c2",a,b,1.0,0.0,2000) is SeasonalWarRatingResult.Frozen)
    }

    private fun repo() = SeasonalEloRepositorySQL(connection, false, SeasonalEloSettings())
    private fun rating(id:UUID)=connection.createStatement().use{it.executeQuery("SELECT elo FROM chapter_seasonal_ratings WHERE chapter_id='c2' AND guild_id='$id'").use{r->check(r.next());r.getInt(1)}}
    private fun progression(id:UUID)=connection.createStatement().use{it.executeQuery("SELECT current_level,total_experience FROM guild_progression WHERE guild_id='$id'").use{r->check(r.next());r.getInt(1) to r.getInt(2)}}
    private fun count(table:String)=connection.createStatement().use{it.executeQuery("SELECT COUNT(*) FROM $table").use{r->check(r.next());r.getInt(1)}}
}
