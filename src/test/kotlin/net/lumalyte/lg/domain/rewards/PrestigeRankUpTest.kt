package net.lumalyte.lg.domain.rewards

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

/** REQ-251: frame characters match the pack's prestige_rankup_v1 font. */
class PrestigeRankUpTest {
    @Test
    fun `tier and frame map to the pack characters`() {
        assertEquals('', PrestigeRankUp.char(1, 0))
        assertEquals('', PrestigeRankUp.char(3, 63))
        assertEquals('', PrestigeRankUp.char(6, 0))
        assertThrows<IllegalArgumentException> { PrestigeRankUp.char(7, 0) }
        assertThrows<IllegalArgumentException> { PrestigeRankUp.char(1, 64) }
    }

    @Test
    fun `members' fanfare follows the animation`() {
        val cues = PrestigeRankUp.memberCues(4)
        assertEquals(7, cues.first().tick)
        assertEquals(11, cues.first { it.sound == "block.amethyst_cluster.break" }.tick)
        assertEquals(16, cues.first { it.sound == "ui.toast.challenge_complete" }.tick)
        assertEquals(PrestigeFanfareScore.cues(1, FanfareAudience.MEMBERS).size, PrestigeRankUp.memberCues(1).size)
    }
}
