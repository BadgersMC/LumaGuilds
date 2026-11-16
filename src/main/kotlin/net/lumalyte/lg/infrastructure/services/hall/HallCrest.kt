package net.lumalyte.lg.infrastructure.services.hall

import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f

/**
 * REQ-277: the guild's prestige badge above a Hall statue's nameplate, still and facing the front.
 * The nameplate's two lines start at [NAMEPLATE_HEIGHT] and reach about [NAMEPLATE_TOP]; the badge sits
 * clear above them so it never covers the guild's name.
 */
internal object HallCrest {
    const val NAMEPLATE_HEIGHT = 2.35
    /** Top of the two-line nameplate with its background, in blocks above the plinth top. */
    const val NAMEPLATE_TOP = 2.9
    const val SCALE = 0.7f
    /** Gap between the nameplate's top and the badge's lower edge. */
    const val GAP = 0.1
    /** Centre of the badge, in blocks above the plinth top. */
    const val HEIGHT = NAMEPLATE_TOP + GAP + SCALE / 2.0

    /** Half a turn shows the item's front to the viewer, as for the banner. */
    fun transformation(): Transformation =
        Transformation(Vector3f(), AxisAngle4f(Math.PI.toFloat(), 0f, 1f, 0f), Vector3f(SCALE, SCALE, SCALE), AxisAngle4f())
}
