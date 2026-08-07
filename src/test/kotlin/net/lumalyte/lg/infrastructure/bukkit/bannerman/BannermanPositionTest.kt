package net.lumalyte.lg.infrastructure.bukkit.bannerman

import org.bukkit.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@Suppress("UndocumentedPublicFunction")
internal class BannermanPositionTest {

    @Test
    fun bannerSitsCenteredJustBelowEye() {
        val eye = Location(null, 10.0, 64.0, 10.0, 0f, 0f)
        val pos = BannermanPosition.headPosition(eye)

        assertEquals(10.0, pos.x, 0.001) // centered — no horizontal offset
        assertEquals(64.0 - 0.3, pos.y, 0.001) // eye - 0.3 (head base)
        assertEquals(10.0, pos.z, 0.001)
    }

    @Test
    fun positionIsIndependentOfViewDirection() {
        val facingSouth = BannermanPosition.headPosition(Location(null, 0.0, 64.0, 0.0, 0f, 0f))
        val facingWest = BannermanPosition.headPosition(Location(null, 0.0, 64.0, 0.0, 90f, 0f))

        assertEquals(facingSouth.x, facingWest.x, 0.001)
        assertEquals(facingSouth.y, facingWest.y, 0.001)
        assertEquals(facingSouth.z, facingWest.z, 0.001)
    }

    @Test
    fun eyeLocationIsNotMutated() {
        val eye = Location(null, 5.0, 64.0, 5.0, 0f, 0f)
        BannermanPosition.headPosition(eye)

        assertEquals(5.0, eye.x, 0.001)
        assertEquals(64.0, eye.y, 0.001)
        assertEquals(5.0, eye.z, 0.001)
    }
}
