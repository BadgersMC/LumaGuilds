package net.lumalyte.lg.infrastructure.services.hall

import org.bukkit.entity.ItemDisplay
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f

/**
 * REQ-277: the guild's prestige badge floating above a Hall statue's nameplate, turning slowly side
 * to side so it reads from the front and catches the light. The client smooths each step.
 */
internal object HallCrest {
    /** Blocks above the plinth top; the nameplate sits at 2.35. */
    const val HEIGHT = 2.95
    const val SCALE = 0.7f
    /** Widest turn either side, and ticks for a full sway there and back. */
    const val SWING_DEGREES = 40.0
    const val PERIOD_TICKS = 100L

    /** Extra turn about the vertical axis at [tick]: 0 facing the viewer, ±[SWING_DEGREES] at the ends. */
    fun angle(tick: Long): Float =
        (Math.toRadians(SWING_DEGREES) * Math.sin(2 * Math.PI * (tick % PERIOD_TICKS) / PERIOD_TICKS)).toFloat()

    /** Half a turn shows the item's front to the viewer (as for the banner), plus the sway. */
    fun transformation(tick: Long): Transformation =
        Transformation(Vector3f(), AxisAngle4f((Math.PI + angle(tick)).toFloat(), 0f, 1f, 0f),
            Vector3f(SCALE, SCALE, SCALE), AxisAngle4f())

    /** Moves [crest] to where it should be [stepTicks] from now, smoothed by the client. */
    fun sway(crest: ItemDisplay, tick: Long, stepTicks: Int) {
        if (!crest.isValid) return
        crest.interpolationDelay = 0
        crest.interpolationDuration = stepTicks
        crest.transformation = transformation(tick + stepTicks)
    }
}
