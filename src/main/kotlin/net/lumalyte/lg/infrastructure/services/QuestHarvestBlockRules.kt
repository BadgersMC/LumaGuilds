package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.domain.values.QuestAction
import org.bukkit.Material
import org.bukkit.block.data.Ageable
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.type.CaveVinesPlant

internal data class QuestBlockBreak(val action: QuestAction, val targetMaterial: Material)

/**
 * Growing vines and kelp have an ageable tip and non-ageable body segments.
 * A harvest quest is about breaking the plant, not waiting for the tip to reach age 25.
 */
internal object QuestHarvestBlockRules {
    private val segmentToTip = mapOf(
        Material.CAVE_VINES_PLANT to Material.CAVE_VINES,
        Material.KELP_PLANT to Material.KELP,
        Material.WEEPING_VINES_PLANT to Material.WEEPING_VINES,
        Material.TWISTING_VINES_PLANT to Material.TWISTING_VINES,
    )
    private val growingTips = segmentToTip.values.toSet()

    fun isVineSegment(material: Material): Boolean = material in segmentToTip

    fun isGrowingPlant(material: Material): Boolean =
        material in segmentToTip || material in growingTips

    fun normalizedTip(material: Material): Material = segmentToTip[material] ?: material

    /** Kelp and twisting vines grow upward; cave and weeping vines grow downward. */
    fun cascadeStep(material: Material): Int? = when (normalizedTip(material)) {
        Material.KELP, Material.TWISTING_VINES -> 1
        Material.CAVE_VINES, Material.WEEPING_VINES -> -1
        else -> null
    }

    fun sameFamily(expected: Material, actual: Material): Boolean =
        isGrowingPlant(actual) && normalizedTip(expected) == normalizedTip(actual)

    fun canHarvestGlowBerries(material: Material, blockData: BlockData): Boolean =
        normalizedTip(material) == Material.CAVE_VINES &&
            isGrowingPlant(material) &&
            blockData is CaveVinesPlant && blockData.hasBerries()

    fun classify(material: Material, blockData: BlockData): QuestBlockBreak {
        val growingTip = segmentToTip[material]
        if (growingTip != null || material in growingTips) {
            return QuestBlockBreak(QuestAction.HARVEST_CROPS, growingTip ?: material)
        }

        val action = if (blockData is Ageable && blockData.age >= blockData.maximumAge) {
            QuestAction.HARVEST_CROPS
        } else {
            QuestAction.MINE_BLOCKS
        }
        return QuestBlockBreak(action, material)
    }
}
