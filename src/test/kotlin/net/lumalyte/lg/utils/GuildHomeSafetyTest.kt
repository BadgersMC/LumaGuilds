package net.lumalyte.lg.utils

import io.mockk.every
import io.mockk.mockk
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
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

    @Test
    fun `GUI safety rejects fire and blocked headspace on both platforms`() {
        val world = mockk<World>()
        val feet = mockk<Block>()
        val ground = mockk<Block>()
        val head = mockk<Block>()
        every { world.getBlockAt(0, 64, 0) } returns feet
        every { world.getBlockAt(0, 63, 0) } returns ground
        every { world.getBlockAt(0, 65, 0) } returns head
        every { world.getBlockAt(any<Location>()) } answers {
            when (firstArg<Location>().blockY) {
                63 -> ground
                64 -> feet
                65 -> head
                else -> error("unexpected test block")
            }
        }
        every { feet.type } returns Material.AIR
        every { ground.type } returns Material.STONE
        every { head.type } returns Material.AIR
        val location = Location(world, 0.5, 64.0, 0.5)

        kotlin.test.assertTrue(GuildHomeSafety.isSafeForUnconfirmedUse(location))
        every { feet.type } returns Material.FIRE
        kotlin.test.assertFalse(GuildHomeSafety.isSafeForUnconfirmedUse(location))
        every { feet.type } returns Material.AIR
        every { head.type } returns Material.STONE
        kotlin.test.assertFalse(GuildHomeSafety.isSafeForUnconfirmedUse(location))
        kotlin.test.assertFalse(GuildHomeSafety.isSafeForUnconfirmedUse(Location(null, 0.0, 64.0, 0.0)))
    }
}
