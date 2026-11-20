package net.lumalyte.lg.domain.rewards

import net.lumalyte.lg.config.PrestigePerksConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** REQ-180, REQ-181: practical perks unlock at prestige tiers. */
class PrestigePerksTest {
    private val perks = PrestigePerks(PrestigePerksConfig())

    @Test
    fun `an unreadable tier gets no perk`() {
        assertEquals(1, perks.allyHomeSlots(null))
        assertEquals(0, perks.extraGuildStalls(null))
    }

    @Test
    fun `second allied home unlocks at Prestige II`() {
        assertEquals(listOf(1, 1, 2, 2), (0..3).map(perks::allyHomeSlots))
    }

    @Test
    fun `extra guild stall unlocks at Prestige III`() {
        assertEquals(listOf(0, 0, 0, 1, 1), (0..4).map(perks::extraGuildStalls))
    }

    @Test
    fun `tiers follow config`() {
        val custom = PrestigePerks(PrestigePerksConfig(secondAllyHomeTier = 4, extraStallTier = 1))
        assertEquals(1, custom.allyHomeSlots(3))
        assertEquals(1, custom.extraGuildStalls(1))
    }

    @Test
    fun `each tier lists the perks it starts`() {
        assertEquals(emptyList(), perks.unlockedAt(1))
        assertEquals(listOf(PrestigePerk.SECOND_ALLY_HOME), perks.unlockedAt(2))
        assertEquals(listOf(PrestigePerk.EXTRA_STALL), perks.unlockedAt(3))
    }
}
