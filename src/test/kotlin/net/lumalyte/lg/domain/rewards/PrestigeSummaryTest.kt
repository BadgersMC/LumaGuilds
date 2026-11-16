package net.lumalyte.lg.domain.rewards

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REQ-249: earned, cost and kept, each line only when true. */
class PrestigeSummaryTest {
    private val base = PrestigePreview(
        tier = 2, finalTier = false, homeSlotsBefore = 3, homeSlotsAfter = 4,
        perks = listOf(PrestigePerk.SECOND_ALLY_HOME), carry = null, memberLimitKept = null,
        discordRoleMinimumLevel = null, memberCreditTenureHours = null, firstOnServer = false,
        fanfare = true, discordPost = false, fee = 16_000,
    )

    @Test
    fun `groups lines into earn, cost and keep`() {
        val lines = PrestigeSummary.lines(base, hasRetainedEffect = true)
        val earn = lines.indexOf(PrestigeSummaryLine.EarnHeading)
        val cost = lines.indexOf(PrestigeSummaryLine.CostHeading)
        val keep = lines.indexOf(PrestigeSummaryLine.KeepHeading)
        assertTrue(earn in 0 until cost && cost < keep)
        assertTrue(lines.subList(earn, cost).containsAll(listOf(
            PrestigeSummaryLine.Badge(2), PrestigeSummaryLine.RetainedReward, PrestigeSummaryLine.RetainedEffect,
            PrestigeSummaryLine.HomeSlot(3, 4), PrestigeSummaryLine.Perk(PrestigePerk.SECOND_ALLY_HOME),
            PrestigeSummaryLine.Announced(fanfare = true, discord = false),
        )))
        assertEquals(listOf(PrestigeSummaryLine.Fee(16_000), PrestigeSummaryLine.LevelReset(false), PrestigeSummaryLine.RewardsReset),
            lines.subList(cost + 1, keep - 1))
    }

    @Test
    fun `optional lines appear only when they apply`() {
        val off = PrestigeSummary.lines(base, hasRetainedEffect = false)
        assertFalse(off.any { it is PrestigeSummaryLine.MemberLimit || it is PrestigeSummaryLine.DiscordRole ||
            it is PrestigeSummaryLine.CarriedXp || it is PrestigeSummaryLine.MemberAdvancement ||
            it is PrestigeSummaryLine.FirstOnServer || it == PrestigeSummaryLine.FinalPrestige ||
            it == PrestigeSummaryLine.RetainedEffect })
        val on = PrestigeSummary.lines(base.copy(
            tier = 6, finalTier = true, carry = PrestigePreview.Carry(1240, 3), memberLimitKept = 100,
            discordRoleMinimumLevel = 50, memberCreditTenureHours = 24, firstOnServer = true, discordPost = true,
        ), hasRetainedEffect = true)
        assertTrue(PrestigeSummaryLine.FinalPrestige in on)
        assertTrue(PrestigeSummaryLine.CarriedXp(1240, 3) in on)
        assertTrue(PrestigeSummaryLine.MemberLimit(100) in on)
        assertTrue(PrestigeSummaryLine.DiscordRole(50) in on)
        assertTrue(PrestigeSummaryLine.MemberAdvancement(6, 24) in on)
        assertTrue(PrestigeSummaryLine.FirstOnServer(6) in on)
        assertTrue(PrestigeSummaryLine.LevelReset(xpKept = true) in on)
    }

    @Test
    fun `zero carried XP is not shown as earned`() {
        val lines = PrestigeSummary.lines(base.copy(carry = PrestigePreview.Carry(0, 1)), hasRetainedEffect = false)
        assertFalse(lines.any { it is PrestigeSummaryLine.CarriedXp })
        assertTrue(PrestigeSummaryLine.LevelReset(xpKept = false) in lines)
    }

    /** REQ-260: the chosen Hall theme is listed with the other things earned, right after the home slot. */
    @Test
    fun `the chosen hall theme is earned`() {
        val theme = PrestigeSummaryLine.HallThemeEarned(HallTheme.EMBER, 2)
        val lines = PrestigeSummary.lines(base, hasRetainedEffect = false, hallTheme = theme)
        assertEquals(lines.indexOf(PrestigeSummaryLine.HomeSlot(3, 4)) + 1, lines.indexOf(theme))
        assertFalse(PrestigeSummary.lines(base, hasRetainedEffect = false).any { it is PrestigeSummaryLine.HallThemeEarned })
    }
}
