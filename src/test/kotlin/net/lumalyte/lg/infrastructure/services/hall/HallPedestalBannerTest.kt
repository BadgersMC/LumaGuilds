package net.lumalyte.lg.infrastructure.services.hall

import net.lumalyte.lg.domain.rewards.HallPedestal
import org.bukkit.block.BlockFace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HallPedestalBannerTest {
    private val pedestal = HallPedestal(1, "world", 42.5, 72.0, -30.5, 0f)

    @Test
    fun `banner spots hang on the four sides of the column under the pedestal, nearest first`() {
        val spots = HallPedestalBanner.spots(pedestal)
        assertEquals(HallPedestalBanner.DEPTH * 4, spots.size)
        assertEquals(Triple(42, 71, -32) to BlockFace.NORTH, spots.first())
        assertTrue(Triple(42, 71, -30) to BlockFace.SOUTH in spots)
        assertTrue(spots.all { (at, _) -> at.second in 68..71 })
        assertFalse(spots.any { (at, _) -> at.first == 42 && at.third == -31 })
    }

    @Test
    fun `the wall banner matches the guild's banner colour, white when it has none`() {
        assertEquals("RED_WALL_BANNER", HallPedestalBanner.wallMaterial("RED_BANNER"))
        assertEquals("LIGHT_BLUE_WALL_BANNER", HallPedestalBanner.wallMaterial("LIGHT_BLUE_BANNER"))
        assertEquals("WHITE_WALL_BANNER", HallPedestalBanner.wallMaterial(null))
        assertEquals("WHITE_WALL_BANNER", HallPedestalBanner.wallMaterial("STONE"))
    }
}
