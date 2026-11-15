package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.domain.values.QuestAction
import org.bukkit.Material
import org.bukkit.block.data.Ageable
import org.bukkit.block.data.BlockData

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
