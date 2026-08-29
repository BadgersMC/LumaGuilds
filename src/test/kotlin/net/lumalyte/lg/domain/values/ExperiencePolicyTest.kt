package net.lumalyte.lg.domain.values

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/** REQ-049/REQ-089 domain invariants for independent and shared XP caps. */
class ExperiencePolicyTest {

    @Test
    fun `ore tiers share one guild-wide allowance pool`() {
        assertEquals("ORE", ExperienceSource.COAL_ORE.defaultPool)
        assertEquals("ORE", ExperienceSource.DIAMOND_ORE.defaultPool)
        assertEquals("ORE", ExperienceSource.ANCIENT_DEBRIS.defaultPool)
    }

    @Test
    fun `craft tiers share one guild-wide allowance pool`() {
        assertEquals("CRAFTING", ExperienceSource.CRAFT_COMMON.defaultPool)
        assertEquals("CRAFTING", ExperienceSource.CRAFT_RARE.defaultPool)
    }

    @Test
    fun `weekly quest activity is unlimited`() {
        val policy = ExperiencePolicy(
            source = ExperienceSource.WEEKLY_ACTIVITY,
            pool = ExperienceSource.WEEKLY_ACTIVITY.defaultPool,
            awardXp = 1,
            capXp = 0,
            period = CapPeriod.UNLIMITED,
            enabled = true,
        )

        assertFalse(policy.isCapped)
    }

    @Test
    fun `daily and weekly policies require positive caps`() {
        for (period in listOf(CapPeriod.DAILY, CapPeriod.WEEKLY)) {
            assertThrows(IllegalArgumentException::class.java) {
                ExperiencePolicy(
                    source = ExperienceSource.MOB_KILL,
                    pool = "MOB_KILL",
                    awardXp = 2,
                    capXp = 0,
                    period = period,
                    enabled = true,
                )
            }
        }
    }

    @Test
    fun `enabled policy requires a positive award`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExperiencePolicy(
                source = ExperienceSource.MOB_KILL,
                pool = "MOB_KILL",
                awardXp = 0,
                capXp = 6_000,
                period = CapPeriod.DAILY,
                enabled = true,
            )
        }
    }

    @Test
    fun `disabled policy may retain a zero award`() {
        val policy = ExperiencePolicy(
            source = ExperienceSource.MOB_KILL,
            pool = "MOB_KILL",
            awardXp = 0,
            capXp = 6_000,
            period = CapPeriod.DAILY,
            enabled = false,
        )

        assertTrue(policy.isCapped)
    }

    @Test
    fun `daily period is aligned to UTC midnight`() {
        val policy = ExperiencePolicy(
            ExperienceSource.MOB_KILL, "MOB_KILL", 2, 6_000, CapPeriod.DAILY, true,
        )

        assertEquals(
            PeriodWindow(
                Instant.parse("2026-08-28T00:00:00Z"),
                Instant.parse("2026-08-29T00:00:00Z"),
            ),
            policy.windowContaining(Instant.parse("2026-08-28T23:59:59Z")),
        )
    }

    @Test
    fun `weekly period is aligned to Monday UTC`() {
        val policy = ExperiencePolicy(
            ExperienceSource.ENDER_DRAGON_KILL,
            "ENDER_DRAGON_KILL",
            1_200,
            12_000,
            CapPeriod.WEEKLY,
            true,
        )

        assertEquals(
            PeriodWindow(
                Instant.parse("2026-08-24T00:00:00Z"),
                Instant.parse("2026-08-31T00:00:00Z"),
            ),
            policy.windowContaining(Instant.parse("2026-08-28T12:00:00Z")),
        )
    }

    @Test
    fun `unlimited policy has no period window`() {
        val policy = ExperiencePolicy(
            ExperienceSource.WEEKLY_ACTIVITY,
            "WEEKLY_ACTIVITY",
            1,
            0,
            CapPeriod.UNLIMITED,
            true,
        )

        assertEquals(null, policy.windowContaining(Instant.parse("2026-08-28T12:00:00Z")))
    }
}
