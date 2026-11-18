package net.lumalyte.lg.infrastructure.services.hall

import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HallCrestTest {
    @Test
    fun `the badge's lower edge sits clear above the nameplate`() {
        val lowerEdge = HallCrest.HEIGHT - HallCrest.SCALE / 2.0
        assertTrue(lowerEdge >= HallCrest.NAMEPLATE_TOP + 0.05) { "badge lower edge $lowerEdge overlaps the nameplate" }
    }

    @Test
    fun `the badge is still and turned half round so its front shows, like the banner`() {
        val facing = Quaternionf(HallCrest.transformation().leftRotation).transform(Vector3f(0f, 0f, 1f))
        assertEquals(-1f, facing.z, 1e-5f)
        assertEquals(HallCrest.SCALE, HallCrest.transformation().scale.x, 1e-6f)
    }
}
