package net.lumalyte.lg.domain.values

import net.lumalyte.lg.domain.rewards.PrestigeRankUp
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** REQ-254: the preview catalog covers every guild sound and mirrors the live definitions. */
class GuildSoundCatalogTest {
    @Test
    fun `ids are unique, typeable and every entry has valid sounds`() {
        assertEquals(GuildSoundCatalog.ids.size, GuildSoundCatalog.ids.toSet().size)
        GuildSoundCatalog.entries.forEach { entry ->
            assertTrue(entry.id.matches(Regex("[a-z0-9-]+")), entry.id)
            assertTrue(entry.cues.isNotEmpty(), entry.id)
            entry.cues.forEach { cue ->
                assertTrue(cue.sound.matches(Regex("[a-z_]+(\\.[a-z_]+)+")), "${entry.id}: ${cue.sound}")
                assertTrue(cue.volume > 0f && cue.pitch in 0.5f..2f, "${entry.id}: ${cue.sound}")
            }
        }
    }

    @Test
    fun `war moments and every prestige tier are covered from the live definitions`() {
        GuildMoment.entries.forEach { moment ->
            assertTrue(GuildSoundCatalog.entries.any { it.cues == moment.cues }, moment.name)
        }
        for (tier in 1..6) {
            val rankUp = assertNotNull(GuildSoundCatalog.find("rankup-$tier"))
            assertEquals(PrestigeRankUp.memberCues(tier).map { it.sound to it.tick.toLong() }, rankUp.cues.map { it.sound to it.delayTicks })
            assertNotNull(GuildSoundCatalog.find("prestige-$tier"))
            assertNotNull(GuildSoundCatalog.find("PRESTIGE-$tier-SERVER"))
        }
    }

    @Test
    fun `length leaves room for delayed cues`() {
        val victory = assertNotNull(GuildSoundCatalog.find("war-victory"))
        assertTrue(victory.lengthTicks >= 78)
        assertEquals(30, assertNotNull(GuildSoundCatalog.find("menu-click")).lengthTicks)
    }
}
