package net.lumalyte.lg.infrastructure.services.hall

import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class HallCrestTest {
    @Test
    fun `the crest faces the viewer at rest and sways to forty degrees either side`() {
        assertEquals(0f, HallCrest.angle(0), 1e-6f)
        assertEquals(Math.toRadians(40.0).toFloat(), HallCrest.angle(25), 1e-5f)
        assertEquals(-Math.toRadians(40.0).toFloat(), HallCrest.angle(75), 1e-5f)
        assertEquals(HallCrest.angle(10), HallCrest.angle(110), 1e-6f)
        (0L..200L).forEach { assertTrue(abs(HallCrest.angle(it)) <= Math.toRadians(40.0) + 1e-6) }
    }

    @Test
    fun `at rest the badge is turned half round so its front shows, like the banner`() {
        val facing = Quaternionf(HallCrest.transformation(0).leftRotation).transform(Vector3f(0f, 0f, 1f))
        assertEquals(-1f, facing.z, 1e-5f)
        assertEquals(HallCrest.SCALE, HallCrest.transformation(0).scale.x, 1e-6f)
    }
}
