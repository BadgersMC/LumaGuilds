package net.lumalyte.lg.infrastructure.bukkit.bannerman

import org.bukkit.Location
import org.bukkit.entity.Pose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@Suppress("UndocumentedPublicFunction")
internal class BannermanPositionTest {

    @Test
    fun bannerSitsForwardOfEyeInUprightPose() {
        val eye = Location(null, 10.0, 64.0, 10.0, 0f, 0f) // yaw 0 = facing +Z
        val pos = BannermanPosition.headPosition(eye, Pose.STANDING)

        assertEquals(10.0, pos.x, 0.001)
        assertEquals(64.0 - 0.3, pos.y, 0.001) // upright drop
        assertEquals(10.0 + 0.2, pos.z, 0.001) // forward nudge along +Z
    }

    @Test
    fun forwardNudgeRotatesWithViewYaw() {
        val eye = Location(null, 0.0, 64.0, 0.0, 90f, 0f) // yaw 90 = facing -X
        val pos = BannermanPosition.headPosition(eye, Pose.STANDING)

        assertEquals(0.0 - 0.2, pos.x, 0.001) // forward = -X
        assertEquals(64.0 - 0.3, pos.y, 0.001)
        assertEquals(0.0, pos.z, 0.001)
    }

    @Test
    fun noVerticalDropInHorizontalBodyPoses() {
        val eye = Location(null, 10.0, 64.0, 10.0, 0f, 0f)
        for (pose in listOf(Pose.SWIMMING, Pose.FALL_FLYING)) {
            val pos = BannermanPosition.headPosition(eye, pose)

            assertEquals(10.0, pos.x, 0.001)
            assertEquals(64.0, pos.y, 0.001) // stays at eye height — body is horizontal
            assertEquals(10.0 + 0.2, pos.z, 0.001)
        }
    }

    @Test
    fun eyeLocationIsNotMutated() {
        val eye = Location(null, 5.0, 64.0, 5.0, 0f, 0f)
        BannermanPosition.headPosition(eye, Pose.STANDING)

        assertEquals(5.0, eye.x, 0.001)
        assertEquals(64.0, eye.y, 0.001)
        assertEquals(5.0, eye.z, 0.001)
    }
}
