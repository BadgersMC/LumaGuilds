package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.domain.rewards.HallDisplay
import net.lumalyte.lg.domain.rewards.HallPedestal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class HallStatuePlanTest {
    private val plan = HallStatueService.Plan(
        HallPedestal(1, "world", 42.5, 72.0, -30.5, 0f), UUID.randomUUID(), "GoldProofOct26", 6,
        HallDisplay(emptyList(), 0, emptyMap(), null), null, "red-banner",
    )

    @Test
    fun `a banner change repaints the hanging banner without rebuilding the statue`() {
        val newBanner = plan.copy(banner = "blue-banner")
        assertNotEquals(plan, newBanner)
        assertEquals(plan.statue(), newBanner.statue())
    }

    @Test
    fun `a prestige or name change still rebuilds the statue`() {
        assertNotEquals(plan.statue(), plan.copy(prestige = 7).statue())
        assertNotEquals(plan.statue(), plan.copy(guildName = "Renamed").statue())
    }
}
