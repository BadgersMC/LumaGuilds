package net.lumalyte.lg.utils

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object GuildHomeSafety {

    private const val TREAT_ANY_LIQUID_UNSAFE = true
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

    fun checkOrAskConfirm(player: Player, target: Location, confirmCommand: String): Boolean {
        val sr = evaluateSafety(target)
        if (sr.safe) return true
        PENDING[player.uniqueId] = Pending(target.clone(), System.currentTimeMillis() + CONFIRM_TTL_MS)
        player.sendMessage("§e[Warning] §7That home looks unsafe: §c${sr.reason}")
        player.sendMessage("§7Type §a$confirmCommand §7within 10s to teleport anyway.")
        return false
    }

    fun consumePending(player: Player): Location? {
        val p = PENDING.remove(player.uniqueId)
        return if (p == null || p.expired()) null else p.loc.clone()
    }

    fun evaluateSafety(base: Location): SafetyResult {
        if (base.world == null) return unsafe("Invalid world or location.")

        val w: World = base.world!!
        val minY = w.minHeight
        val maxY = w.maxHeight

        if (base.y < minY + 1 || base.y > maxY - 2) return unsafe("Location height is out of range.")

        val feetLoc = base.clone()
        val headLoc = base.clone().add(0.0, 1.0, 0.0)
        val belowLoc = base.clone().add(0.0, -1.0, 0.0)

        val feet: Block = feetLoc.block
        val head: Block = headLoc.block
        val below: Block = belowLoc.block

        if (!feet.isPassable) return unsafe("No space at feet.")
        if (!head.isPassable) return unsafe("No space at head height.")

        if (below.isPassable) {
            var airBelow = 0
            for (i in 1..3) {
                if (belowLoc.clone().add(0.0, -i.toDouble(), 0.0).block.isPassable) airBelow++
            }
            if (airBelow >= 3) return unsafe("Long drop below.")
        }

        if (DAMAGING.contains(feet.type)) return unsafe("Damaging block at feet.")
        if (DAMAGING.contains(below.type)) return unsafe("Damaging block below.")

        if (TREAT_ANY_LIQUID_UNSAFE && (feet.isLiquid || below.isLiquid)) return unsafe("Liquid at/below location.")

        return safe()
    }

    private fun safe(): SafetyResult = SafetyResult(true, null)
    private fun unsafe(reason: String): SafetyResult = SafetyResult(false, reason)

    data class SafetyResult(val safe: Boolean, val reason: String?)
}
