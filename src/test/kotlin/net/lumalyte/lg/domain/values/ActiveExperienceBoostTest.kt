package net.lumalyte.lg.domain.values

import net.lumalyte.lg.config.WeekendBoostConfig
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** REQ-178: 2x XP for every guild on Saturday and Sunday, New York time. */
class ActiveExperienceBoostTest {
    private val weekend = WeekendBoostConfig()

    // 2026-11-14 is a Saturday; New York is UTC-5 in November.
    private fun xp(at: String, source: ExperienceSource = ExperienceSource.MOB_KILL, dated: ExperienceBoost? = null,
                   settings: WeekendBoostConfig = weekend): Int {
        val now = Instant.parse(at)
        return ActiveExperienceBoost.at(now, dated, settings)?.apply(100, source, now) ?: 100
    }

    @Test
    fun `weekend days in New York double activity XP`() {
        assertEquals(200, xp("2026-11-14T05:00:00Z"))   // Saturday 00:00 New York
        assertEquals(200, xp("2026-11-16T04:59:59Z"))   // Sunday 23:59:59 New York
        assertEquals(100, xp("2026-11-14T04:59:59Z"))   // Friday 23:59:59 New York
        assertEquals(100, xp("2026-11-16T05:00:00Z"))   // Monday 00:00 New York
    }

    @Test
    fun `quest and admin XP are never doubled`() {
        assertEquals(100, xp("2026-11-14T15:00:00Z", ExperienceSource.WEEKLY_ACTIVITY))
        assertEquals(100, xp("2026-11-14T15:00:00Z", ExperienceSource.ADMIN_BONUS))
        assertEquals(100, xp("2026-11-14T15:00:00Z", ExperienceSource.GUILD_ADVANCEMENT)) // REQ-227
    }

    @Test
    fun `a dated boost and the weekend never stack`() {
        val at = Instant.parse("2026-11-14T15:00:00Z")
        val big = ExperienceBoost(at.minusSeconds(60), at.plusSeconds(60), 3.0, setOf(ExperienceSource.MOB_KILL))
        val small = ExperienceBoost(at.minusSeconds(60), at.plusSeconds(60), 1.5, setOf(ExperienceSource.MOB_KILL))
        assertEquals(300, xp("2026-11-14T15:00:00Z", dated = big))
        assertEquals(200, xp("2026-11-14T15:00:00Z", dated = small))
        assertEquals(150, xp("2026-11-12T15:00:00Z", dated = ExperienceBoost(
            Instant.parse("2026-11-12T14:00:00Z"), Instant.parse("2026-11-12T16:00:00Z"), 1.5, setOf(ExperienceSource.MOB_KILL))))
    }

    @Test
    fun `disabled weekend boost does nothing`() {
        assertNull(ActiveExperienceBoost.at(Instant.parse("2026-11-14T15:00:00Z"), null, WeekendBoostConfig(enabled = false)))
    }
}
