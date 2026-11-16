package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.domain.rewards.HallDisplay
import net.lumalyte.lg.domain.rewards.HallSlot
import net.lumalyte.lg.domain.rewards.HallTheme
import org.bukkit.Material
import org.bukkit.Particle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HallLookTest {
    private fun look(owned: List<HallTheme>, choices: Map<HallSlot, HallTheme> = emptyMap(), tier: Int = 1) =
        HallLook(HallDisplay(owned, 0, choices, null), tier)

    @Test
    fun `every theme maps to a distinct plinth, trophy and aura`() {
        val looks = HallTheme.entries.map { look(listOf(it)) }
        assertEquals(6, looks.map { it.plinth }.toSet().size)
        assertEquals(6, looks.map { it.trophy }.toSet().size)
        assertEquals(6, looks.map { it.aura }.toSet().size)
    }

    @Test
    fun `slot choices are mixed across owned themes`() {
        val l = look(listOf(HallTheme.GILDED, HallTheme.TIDAL), mapOf(HallSlot.PLINTH to HallTheme.TIDAL))
        assertEquals(Material.PRISMARINE_BRICKS, l.plinth)
        assertEquals(Particle.WAX_ON, l.aura)
    }

    @Test
    fun `title flourish wraps the guild name`() {
        assertEquals("♛ Enthusia ♛", look(listOf(HallTheme.CROWNED)).nameplate("Enthusia"))
        assertEquals("Enthusia", look(emptyList()).nameplate("Enthusia"))
    }
}
