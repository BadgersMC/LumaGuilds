package net.lumalyte.lg.domain.rewards

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** REQ-164: the bell climb into the challenge chime (TEST preview 8), cue for cue. */
class PrestigeFanfareScoreTest {
    private val bell = "block.note_block.bell"
    private val chime = "ui.toast.challenge_complete"

    @Test
    fun `members hear three rising bells into the challenge chime`() {
        val cues = PrestigeFanfareScore.cues(tier = 1, audience = FanfareAudience.MEMBERS)
        assertEquals(
            listOf(
                FanfareCue(0, bell, 0.55f, 0.849f),
                FanfareCue(3, bell, 0.55f, 1.07f),
                FanfareCue(6, bell, 0.6f, 1.273f),
                FanfareCue(9, chime, 1.0f, 0.9f),
                FanfareCue(9, bell, 0.5f, 1.699f),
            ),
            cues,
        )
    }

    @Test
    fun `every tier hears the same fanfare`() {
        val first = PrestigeFanfareScore.cues(1, FanfareAudience.MEMBERS)
        for (tier in 2..6) assertEquals(first, PrestigeFanfareScore.cues(tier, FanfareAudience.MEMBERS))
    }

    @Test
    fun `everyone else hears the same fanfare quietly`() {
        val members = PrestigeFanfareScore.cues(3, FanfareAudience.MEMBERS)
        val server = PrestigeFanfareScore.cues(3, FanfareAudience.SERVER)
        assertEquals(members.map { it.tick to it.sound to it.pitch }, server.map { it.tick to it.sound to it.pitch })
        assertTrue(server.all { it.volume <= 0.3f })
    }

    @Test
    fun `no flute, horn or beacon, at audible volumes and valid pitches`() {
        for (tier in 1..6) for (audience in FanfareAudience.entries) {
            PrestigeFanfareScore.cues(tier, audience).forEach { cue ->
                assertTrue(cue.sound in setOf(bell, chime), cue.sound)
                assertTrue(cue.volume in 0.05f..1f && cue.pitch in 0.5f..2f, cue.toString())
            }
        }
    }
}
