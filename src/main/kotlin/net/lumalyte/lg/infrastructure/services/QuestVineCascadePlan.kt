package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.domain.values.BlockPosition
import org.bukkit.Material

/**
 * Capture only the same connected stalk which a player break can disconnect.
 * Physics removals do not each generate their own player BlockBreakEvent.
 */
internal object QuestVineCascadePlan {
    const val MAX_SEGMENTS = 384

    fun captureYs(
        origin: Material,
        startY: Int,
        minY: Int,
        maxYExclusive: Int,
        materialAt: (Int) -> Material,
    ): List<Int> {
        val step = QuestHarvestBlockRules.cascadeStep(origin) ?: return emptyList()
        val ys = ArrayList<Int>()
        var y = startY
        repeat(MAX_SEGMENTS) {
            if (y < minY || y >= maxYExclusive ||
                !QuestHarvestBlockRules.sameFamily(origin, materialAt(y))) return ys
            ys.add(y)
            y += step
        }
        return ys
    }

    /** Verify actual removals on the following tick, not merely predicted physics. */
    fun removedYs(
        origin: Material,
        candidates: List<Int>,
        materialAt: (Int) -> Material,
    ): List<Int> = candidates.filterNot { QuestHarvestBlockRules.sameFamily(origin, materialAt(it)) }
}

/** Protects against overlapping player breaks claiming the same fallen segment. Main-thread only. */
internal class RecentVineCredits {
    private val credited = LinkedHashMap<BlockPosition, Long>()

    fun record(position: BlockPosition, tick: Long): Boolean {
        val last = credited[position]
        if (last != null && tick >= last && tick - last <= 2) return false
        credited[position] = tick
        if (credited.size > 4096) credited.remove(credited.keys.first())
        return true
    }
}
