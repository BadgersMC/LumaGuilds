package net.lumalyte.lg.domain.values

import net.lumalyte.lg.domain.entities.WarNotificationKind
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** REQ-233 to REQ-235: each moment has its own sound, matching the approved mockup. */
internal class GuildMomentTest {
    @Test fun `every moment has a sound and each mockup moment sounds different`() {
        GuildMoment.entries.forEach { assertTrue(it.cues.isNotEmpty(), "$it has no sound") }
        val signatures = listOf(GuildMoment.DECLARED, GuildMoment.VICTORY, GuildMoment.DEFEAT,
            GuildMoment.PEACE, GuildMoment.ALLIANCE).map { m -> m.cues.map { it.sound to it.pitch } }
        assertEquals(signatures.size, signatures.distinct().size, "Moments must not share the same sound")
        // The mockup plays the same cue for a declaration and the war starting.
        assertEquals(GuildMoment.DECLARED.cues, GuildMoment.BEGINS.cues)
    }

    @Test fun `cues follow the mockup recipe`() {
        // war-redesign.js bells .51 @.85 and .42 @.8 after 200 ms; REQ-280 swaps the wither roar for a bass note
        assertEquals(listOf("block.bell.use", "block.note_block.bass", "block.bell.use"), GuildMoment.DECLARED.cues.map { it.sound })
        // victory: triumph, reward (harp D then A), amethyst chime @1.15 after 1050 ms
        assertEquals("ui.toast.challenge_complete", GuildMoment.VICTORY.cues.first().sound)
        assertTrue(GuildMoment.VICTORY.cues.any { it.sound == "block.amethyst_block.chime" && it.delayTicks == 21L })
        assertEquals(listOf("block.bell.use", "block.note_block.bass", "block.bell.use"), GuildMoment.DEFEAT.cues.map { it.sound })
        assertEquals(listOf("block.note_block.harp", "block.note_block.harp", "block.amethyst_block.chime"), GuildMoment.PEACE.cues.map { it.sound })
    }

    @Test fun `REQ-280 no moment cuts a sound short or uses the wither roar`() {
        GuildMoment.entries.forEach { moment ->
            assertTrue(moment.cues.all { it.stopAfterTicks == null }, "${moment.name} stops a sound early, which is a hard cut in game")
            assertTrue(moment.cues.none { it.sound == "entity.wither.spawn" }, "${moment.name} uses the wither roar")
        }
    }

    @Test fun `a declaration rings two bells then lands a low impact`() {
        val cues = GuildMoment.DECLARED.cues
        val bells = cues.filter { it.sound == "block.bell.use" }
        assertEquals(2, bells.size)
        assertEquals(0L, bells[0].delayTicks)
        assertTrue(bells[1].delayTicks in 3L..5L, "second bell about 200 ms later")
        assertTrue(bells[1].pitch < bells[0].pitch && bells[1].volume < bells[0].volume, "second bell lower and quieter")
        assertTrue(cues.any { it.sound != "block.bell.use" && it.pitch < 1f }, "low impact underneath")
    }

    @Test fun `a war kill pairs a slowed crit with a quick level up`() {
        val (crit, chime) = GuildMoment.KILL.cues
        assertEquals("entity.player.attack.crit", crit.sound)
        assertTrue(crit.pitch < 1f)
        assertEquals("entity.player.levelup", chime.sound)
        assertTrue(chime.pitch > 1f)
    }

    @Test fun `war notifications map onto mockup moments`() {
        assertEquals(GuildMoment.DECLARED, GuildMoment.of(WarNotificationKind.DECLARATION_RECEIVED))
        assertEquals(GuildMoment.DECLARED, GuildMoment.of(WarNotificationKind.DECLARATION_SENT))
        assertEquals(GuildMoment.BEGINS, GuildMoment.of(WarNotificationKind.WAR_ACCEPTED))
        assertEquals(GuildMoment.VICTORY, GuildMoment.of(WarNotificationKind.VICTORY))
        assertEquals(GuildMoment.DEFEAT, GuildMoment.of(WarNotificationKind.DEFEAT))
        assertNotEquals(GuildMoment.VICTORY.cues, GuildMoment.DEFEAT.cues)
    }

    @Test fun `the declaring guild is named first whichever side receives it`() {
        assertEquals("Pengu Family" to "Enthusiast",
            GuildMoment.declaringFirst(WarNotificationKind.DECLARATION_RECEIVED, own = "Enthusiast", opponent = "Pengu Family"))
        assertEquals("Pengu Family" to "Enthusiast",
            GuildMoment.declaringFirst(WarNotificationKind.DECLARATION_SENT, own = "Pengu Family", opponent = "Enthusiast"))
    }
}
