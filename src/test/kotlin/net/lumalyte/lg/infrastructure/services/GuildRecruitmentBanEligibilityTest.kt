package net.lumalyte.lg.infrastructure.services

import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFalse

/** REQ-194: a player banned from a guild cannot apply to it or join it through recruitment. */
internal class GuildRecruitmentBanEligibilityTest {
    @Test
    fun `banned from the guild means not eligible`() {
        val guild = UUID.randomUUID()
        val player = UUID.randomUUID()
        val eligibility = GuildRecruitmentEligibilityBukkit(
            mockk(relaxed = true), mockk(relaxed = true), banned = { false },
            guildBanned = { g, p -> g == guild && p == player },
        )
        assertFalse(eligibility.allows(player, guild))
    }
}
