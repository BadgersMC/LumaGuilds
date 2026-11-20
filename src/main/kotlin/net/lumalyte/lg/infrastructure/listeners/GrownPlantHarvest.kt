package net.lumalyte.lg.infrastructure.listeners

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace

/**
 * REQ-177: sugar cane, cactus, kelp and bamboo use their age as a growth timer, so
 * "fully grown" says nothing about a harvest. Breaking one counts it and the
 * blocks of the same plant above it, which break with it.
 */
internal data class GrownPlantHarvest(val target: String, val blocks: List<Block>) {
    companion object {
        private const val MAX_HEIGHT = 32
        private val FAMILIES = mapOf(
            Material.SUGAR_CANE to "sugar_cane",
            Material.CACTUS to "cactus",
            Material.BAMBOO to "bamboo",
            Material.KELP to "kelp",
            Material.KELP_PLANT to "kelp",
        )

        fun of(block: Block): GrownPlantHarvest? {
            val family = FAMILIES[block.type] ?: return null
            val column = generateSequence(block) { it.getRelative(BlockFace.UP) }
                .takeWhile { FAMILIES[it.type] == family }
                .take(MAX_HEIGHT)
                .toList()
            return GrownPlantHarvest("minecraft:block/$family", column)
        }
    }
}
