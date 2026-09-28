package net.lumalyte.lg.config

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PrestigeConfigSeason2Test {
    @Test
    fun `season two default allows six lifetime prestiges`() {
        val config = PrestigeConfig()
        assertEquals(6, config.maxCount)
        assertEquals(
            listOf(10_000L, 20_000L, 30_000L, 30_000L, 30_000L, 30_000L),
            config.fees,
        )
        assertEquals(30_000L, config.feeFor(5))
    }

    @Test
    fun `prestige maximum cannot exceed six`() {
        assertFailsWith<IllegalArgumentException> {
            PrestigeConfig(
                maxCount = 7,
                fees = listOf(10_000L, 20_000L, 30_000L, 40_000L, 50_000L, 60_000L, 70_000L),
            )
        }
    }
}
