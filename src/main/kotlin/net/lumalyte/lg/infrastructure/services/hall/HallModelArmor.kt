package net.lumalyte.lg.infrastructure.services.hall

import net.lumalyte.lg.domain.rewards.HallArmorMaterial
import net.lumalyte.lg.domain.rewards.HallArmorPiece

/**
 * REQ-296: one armor layer as BetterModel's player model draws it. [type] is the vanilla equipment
 * texture name (textures/entity/equipment/humanoid/<type>.png), [tint] an RGB dye (white when undyed),
 * [trim] a trim pattern and [palette] its trim palette, "_darker" when the trim matches the armor.
 */
internal data class HallModelArmorLayer(val type: String, val tint: Int, val trim: String?, val palette: String?)

internal object HallModelArmor {
    const val WHITE = 0xFFFFFF
    /** Vanilla's leather colour when it isn't dyed. */
    const val LEATHER = 0xA06540
    private val darker = setOf("copper", "diamond", "gold", "iron", "netherite")

    fun type(material: HallArmorMaterial): String = when (material) {
        HallArmorMaterial.GOLDEN -> "gold"
        else -> material.name.lowercase()
    }

    fun layer(piece: HallArmorPiece): HallModelArmorLayer {
        val type = type(piece.material)
        val tint = when {
            piece.material.dyeable -> piece.dye ?: LEATHER
            else -> WHITE
        }
        val palette = piece.trimMaterial?.let { if (it == type && it in darker) "${it}_darker" else it }
        return HallModelArmorLayer(type, tint, piece.trimPattern.takeIf { palette != null }, palette)
    }
}
