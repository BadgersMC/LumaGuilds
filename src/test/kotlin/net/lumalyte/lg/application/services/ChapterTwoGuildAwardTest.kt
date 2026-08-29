package net.lumalyte.lg.application.services

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChapterTwoGuildAwardTest {
    @Test
    fun `withdraw and redeposit does not create net-new bank XP`() {
        assertEquals(100, ChapterTwoGuildAwardRules.netNewBankUnits(0, 10_000, 100))
        assertEquals(0, ChapterTwoGuildAwardRules.netNewBankUnits(10_000, 0, 100))
        assertEquals(0, ChapterTwoGuildAwardRules.netNewBankUnits(10_000, 10_000, 100))
        assertEquals(10, ChapterTwoGuildAwardRules.netNewBankUnits(10_000, 11_000, 100))
    }

    @Test
    fun `small deposits accumulate across the high-water boundary`() {
        assertEquals(1, ChapterTwoGuildAwardRules.netNewBankUnits(50, 100, 100))
    }

    @Test
    fun `recruit awards after seven retained days exactly once`() {
        val joined = Instant.parse("2026-08-01T00:00:00Z")
        assertFalse(ChapterTwoGuildAwardRules.recruitQualifies(joined, Instant.parse("2026-08-07T23:59:59Z"), true, false))
        assertTrue(ChapterTwoGuildAwardRules.recruitQualifies(joined, Instant.parse("2026-08-08T00:00:00Z"), true, false))
        assertFalse(ChapterTwoGuildAwardRules.recruitQualifies(joined, Instant.parse("2026-08-08T00:00:00Z"), false, false))
        assertFalse(ChapterTwoGuildAwardRules.recruitQualifies(joined, Instant.parse("2026-08-08T00:00:00Z"), true, true))
    }

    @Test
    fun `permanent war XP stops at level one hundred`() {
        assertTrue(ChapterTwoGuildAwardRules.warWinQualifies(99))
        assertFalse(ChapterTwoGuildAwardRules.warWinQualifies(100))
        assertFalse(ChapterTwoGuildAwardRules.warWinQualifies(101))
    }
}
