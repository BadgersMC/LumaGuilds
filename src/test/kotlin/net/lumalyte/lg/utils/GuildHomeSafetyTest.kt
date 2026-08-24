package net.lumalyte.lg.utils

import io.mockk.every
import io.mockk.mockk
import org.bukkit.Location
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GuildHomeSafetyTest {

    @Test
    fun `unsafe check returns a typed issue without sending player copy`() {
        val player = mockk<Player>(relaxed = true) {
            every { uniqueId } returns UUID.randomUUID()
        }
        val target = Location(null, 0.0, 64.0, 0.0)

        val result = GuildHomeSafety.checkAndRemember(player, target)

        assertEquals(GuildHomeSafety.Issue.INVALID_WORLD, result.issue)
        assertNotNull(GuildHomeSafety.consumePending(player))
        io.mockk.verify(exactly = 0) { player.sendMessage(any<String>()) }
    }
}
