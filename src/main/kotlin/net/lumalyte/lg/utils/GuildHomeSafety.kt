package net.lumalyte.lg.utils

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object GuildHomeSafety {

    private const val CONFIRM_TTL_MS = 10_000L

    private val DAMAGING = EnumSet.of(
        Material.LAVA,
        Material.FIRE,
        Material.SOUL_FIRE,
        Material.CAMPFIRE,
        Material.SOUL_CAMPFIRE,
        Material.MAGMA_BLOCK,
        Material.CACTUS,
        Material.POINTED_DRIPSTONE,
        Material.SWEET_BERRY_BUSH,
        Material.WITHER_ROSE
    )

    private data class Pending(val loc: Location, val expiresAt: Long) {
        fun expired(): Boolean = System.currentTimeMillis() > expiresAt
    }

    private val PENDING = ConcurrentHashMap<UUID, Pending>()

    fun checkAndRemember(player: Player, target: Location): SafetyResult {
        val result = evaluateSafety(target)
        if (!result.safe) {
            PENDING[player.uniqueId] = Pending(target.clone(), System.currentTimeMillis() + CONFIRM_TTL_MS)
        }
        return result
    }

    fun consumePending(player: Player): Location? {
        val p = PENDING.remove(player.uniqueId)
        return if (p == null || p.expired()) null else p.loc.clone()
    }

    fun evaluateSafety(base: Location): SafetyResult {
        val w: World = base.world ?: return unsafe(Issue.INVALID_WORLD)
        val minY = w.minHeight
        val maxY = w.maxHeight

        if (base.y < minY + 1 || base.y > maxY - 2) return unsafe(Issue.HEIGHT_OUT_OF_RANGE)

        val feetLoc = base.clone()
        val belowLoc = base.clone().add(0.0, -1.0, 0.0)

        val feet: Block = feetLoc.block
        val below: Block = belowLoc.block

        if (DAMAGING.contains(feet.type)) return unsafe(Issue.DAMAGING_BLOCK_AT_FEET)
        if (DAMAGING.contains(below.type)) return unsafe(Issue.DAMAGING_BLOCK_BELOW)

        return safe()
    }

    private fun safe(): SafetyResult = SafetyResult(true, null)
    private fun unsafe(issue: Issue): SafetyResult = SafetyResult(false, issue)

    data class SafetyResult(val safe: Boolean, val issue: Issue?)

    enum class Issue {
        INVALID_WORLD,
        HEIGHT_OUT_OF_RANGE,
        DAMAGING_BLOCK_AT_FEET,
        DAMAGING_BLOCK_BELOW,
    }
}
