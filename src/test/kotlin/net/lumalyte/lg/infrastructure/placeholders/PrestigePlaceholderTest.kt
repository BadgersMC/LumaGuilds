package net.lumalyte.lg.infrastructure.placeholders

import net.lumalyte.lg.domain.rewards.PrestigeStanding
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

/** REQ-155: prestige placeholder formatting. */
class PrestigePlaceholderTest {
    private val badges = listOf("", "b1", "b2", "b3", "b4", "b5", "b6")
    private val crowned = PrestigeStanding(6, Instant.parse("2026-10-24T23:30:00Z"), 1)
    private val fresh = PrestigeStanding(0, null, 0)

    private fun v(id: String, standing: PrestigeStanding?) = PrestigePlaceholder.value(id, standing, badges, 6)

    @Test
    fun `formats every prestige placeholder for a prestiged guild`() {
        assertEquals("6", v("guild_prestige", crowned))
        assertEquals("VI", v("guild_prestige_roman", crowned))
        assertEquals("b6", v("guild_prestige_badge", crowned))
        assertEquals("6", v("guild_prestige_max", crowned))
        assertEquals("1", v("guild_prestige_first_tier", crowned))
        assertEquals("2026-10-24", v("guild_prestige_last_date", crowned))
    }

    @Test
    fun `tier zero shows zero but no roman numeral, badge or date`() {
        assertEquals("0", v("guild_prestige", fresh))
        assertEquals("", v("guild_prestige_roman", fresh))
        assertEquals("", v("guild_prestige_badge", fresh))
        assertEquals("0", v("guild_prestige_first_tier", fresh))
        assertEquals("", v("guild_prestige_last_date", fresh))
        assertEquals("6", v("guild_prestige_max", fresh))
    }

    @Test
    fun `dates use UTC regardless of the time of day`() {
        val late = PrestigeStanding(1, Instant.parse("2026-11-01T03:59:00Z"), 0)
        assertEquals("2026-11-01", v("guild_prestige_last_date", late))
    }

    @Test
    fun `an unreadable standing is blank, never a fabricated tier`() {
        listOf("guild_prestige", "guild_prestige_roman", "guild_prestige_badge",
            "guild_prestige_first_tier", "guild_prestige_last_date").forEach {
            assertEquals("", v(it, null), it)
        }
        assertEquals("6", v("guild_prestige_max", null))
    }

    @Test
    fun `a short badge list falls back to blank and unknown identifiers are blank`() {
        assertEquals("", PrestigePlaceholder.value("guild_prestige_badge", crowned, listOf("", "b1"), 6))
        assertEquals("", v("guild_prestige_unknown", crowned))
    }

    @org.junit.jupiter.api.Test
    fun `badge for proxy tab lists is MiniMessage`() {
        val badges = listOf("", "&f\uB025", "&6✦II")
        val one = PrestigeStanding(1, null, 0)
        assertEquals("<white>\uB025", PrestigePlaceholder.value("guild_prestige_badge_minimessage", one, badges, 6))
        assertEquals("", PrestigePlaceholder.value("guild_prestige_badge_minimessage", PrestigeStanding(0, null, 0), badges, 6))
    }

    @org.junit.jupiter.api.Test
    fun `badge glyphs keep their Nexo font so the client draws the icon`() {
        val badges = listOf("", "&f\uB025")
        val fontOf: (Char) -> String? = { if (it == '\uB025') "nexo:default" else null }
        assertEquals(
            "<white><font:nexo:default>\uB025</font>",
            PrestigePlaceholder.value("guild_prestige_badge_minimessage", PrestigeStanding(1, null, 0), badges, 6, fontOf),
        )
    }
}
