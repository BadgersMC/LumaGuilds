package net.lumalyte.lg.infrastructure.bukkit.bannerman

import org.bukkit.Location
import org.bukkit.entity.Pose
import org.bukkit.util.Vector

/**
 * Pure position math for the bannerman display. Kept free of Bukkit state so
 * the offset logic is trivially unit-testable (same pattern as [BannermanVisibility]).
 *
 * The display uses the HEAD item-display transform, so the banner renders as the
 * full-size banner block anchored at the display point — like a banner worn in the
 * helmet slot. Position is a small forward nudge from the eye (so the pole sits at
 * the top center of the head), plus a vertical drop that only applies in upright
 * poses. When flying/swimming the body is horizontal — the banner pole runs along the
 * body axis instead (see BannermanTickTask), and the eye is already at the head, so
 * applying the upright drop would sink the banner to the feet.
 */
object BannermanPosition {

    /** Forward nudge along the view direction so the pole sits at the head's center. */
    private const val FORWARD_OFFSET = 0.2

    /** Vertical drop from the eye in upright poses (banner base around head level). */
    private const val UPRIGHT_DROP_Y = -0.3

    /** Poses where the body is horizontal (pole runs along the body axis, no drop). */
    private val HORIZONTAL_BODY_POSES = setOf(Pose.SWIMMING, Pose.FALL_FLYING)

    /**
     * @param eye the player's eye location
     * @param pose the player's current pose
     * @return where the banner display should sit.
     */
    fun headPosition(eye: Location, pose: Pose): Location {
        // Horizontal-only forward vector derived from yaw, so the offset stays exactly
        // FORWARD_OFFSET at any view pitch (direction.setY(0) would shrink by cos(pitch)
        // and vanish when looking straight up or down).
        val yawRad = Math.toRadians(eye.yaw.toDouble())
        val forward = Vector(
            -Math.sin(yawRad),
            0.0,
            Math.cos(yawRad),
        ).multiply(FORWARD_OFFSET)
        val drop = if (pose in HORIZONTAL_BODY_POSES) 0.0 else UPRIGHT_DROP_Y
        return eye.clone().add(forward).add(0.0, drop, 0.0)
    }
}
