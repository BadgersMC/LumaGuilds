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

class HallPoseNameTest {
    @Test
    fun `REQ-272 pose names map to the model's animation names and Guard is the fallback`() {
        assertEquals("guard", net.lumalyte.lg.infrastructure.services.hall.HallStatuePose.animationFor("Guard"))
        assertEquals("victory", net.lumalyte.lg.infrastructure.services.hall.HallStatuePose.animationFor(" Victory "))
        assertEquals("guard", net.lumalyte.lg.infrastructure.services.hall.HallStatuePose.animationFor(""))
        assertEquals("Guard", HallLook(HallDisplay(emptyList(), 0, emptyMap(), null), 1).pose)
    }

    @Test
    fun `REQ-269 prestige badge ids follow the tier and stop at six`() {
        assertEquals("lg_prestige", net.lumalyte.lg.interaction.menus.guild.PrestigeIcon.id(0))
        assertEquals("lg_prestige_3", net.lumalyte.lg.interaction.menus.guild.PrestigeIcon.id(3))
        assertEquals("lg_prestige_6", net.lumalyte.lg.interaction.menus.guild.PrestigeIcon.id(9))
    }
}
