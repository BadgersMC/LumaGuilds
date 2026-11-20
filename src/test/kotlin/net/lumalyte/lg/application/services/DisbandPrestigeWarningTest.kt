package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.domain.rewards.PrestigeStanding
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REQ-162: which disband warning, if any, a guild gets. */
class DisbandPrestigeWarningTest {
    private val guild = UUID.randomUUID()

    private fun standing(value: PrestigeStanding?) = mockk<GuildPrestigeStandings> {
        every { standing(guild) } returns value
    }

    @Test
    fun `never prestiged keeps the old flow`() {
        val warning = DisbandPrestigeWarning.of(standing(PrestigeStanding(0, null, 0)), guild)
        assertEquals(DisbandPrestigeWarning.None, warning)
        assertFalse(warning.needsConfirmation)
    }

    @Test
    fun `a prestiged guild is warned with its tier`() {
        val warning = DisbandPrestigeWarning.of(standing(PrestigeStanding(2, null, 1)), guild)
        assertEquals(DisbandPrestigeWarning.Tier(2), warning)
        assertTrue(warning.needsConfirmation)
    }

    @Test
    fun `an unreadable tier still warns`() {
        val warning = DisbandPrestigeWarning.of(standing(null), guild)
        assertEquals(DisbandPrestigeWarning.Unknown, warning)
        assertTrue(warning.needsConfirmation)
    }
}
