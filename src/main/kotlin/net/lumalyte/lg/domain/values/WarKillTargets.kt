package net.lumalyte.lg.domain.values

import net.lumalyte.lg.domain.entities.ObjectiveType
import net.lumalyte.lg.domain.entities.WarObjective

/** REQ-230: each war ends at its own kill target. */
object WarKillTargets {
    private val PRESETS = listOf(5, 10, 25, 50, 100)

    /** The war's kill objective, or [default] when it has none. */
    fun forWar(objectives: Collection<WarObjective>, default: Int): Int =
        objectives.filter { it.type == ObjectiveType.KILLS && it.targetValue > 0 }.minOfOrNull { it.targetValue } ?: default

    /** Targets a guild may choose: the presets and [default], up to [max] (never below [default]). */
    fun options(default: Int, max: Int): List<Int> {
        val cap = maxOf(max, default)
        return (PRESETS.filter { it <= cap } + default).filter { it > 0 }.distinct().sorted()
    }

    /** The largest kill target a guild may choose. */
    fun maximum(default: Int, max: Int): Int = maxOf(max, default)
}
