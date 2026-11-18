package net.lumalyte.lg.infrastructure.services.hall

import kr.toxicity.model.api.BetterModel
import kr.toxicity.model.api.animation.AnimationIterator
import kr.toxicity.model.api.animation.AnimationModifier
import kr.toxicity.model.api.armor.ArmorItem
import kr.toxicity.model.api.armor.PlayerArmor
import kr.toxicity.model.api.bone.BoneItemMapper
import kr.toxicity.model.api.bone.BoneRenderContext
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter
import kr.toxicity.model.api.platform.PlatformItemTransform
import kr.toxicity.model.api.player.PlayerLimb
import kr.toxicity.model.api.profile.ModelProfile
import kr.toxicity.model.api.tracker.EntityTracker
import kr.toxicity.model.api.util.TransformedItemStack
import net.lumalyte.lg.domain.rewards.HallArmorPart
import net.lumalyte.lg.domain.rewards.HallArmorPiece
import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * REQ-272: poses the Hall statue with the BetterModel player-limb model [LIMB] (the same model the
 * Hall of Prestige NPCs use). Only touched when BetterModel is enabled, so its classes load lazily.
 */
internal class HallStatuePose private constructor() {
    private val logger = LoggerFactory.getLogger(HallStatuePose::class.java)

    /**
     * Wraps [statue] in the limb model wearing [skin]'s skin and the guild's [armor], seen only by players
     * [canSee] accepts, looping the Guard pose (REQ-283: the only pose). Returns a closer, or null if the
     * model is missing (the plain statue then stays visible).
     */
    fun apply(
        statue: LivingEntity,
        canSee: (UUID) -> Boolean,
        skin: UUID?,
        armor: Map<HallArmorPart, HallArmorPiece> = emptyMap(),
    ): (() -> Unit)? = try {
        val renderer = BetterModel.limb(LIMB).orElse(null) ?: return null
        val entity = BukkitAdapter.adapt(statue)
        val tracker: EntityTracker = if (skin != null) renderer.create(entity, ModelProfile.of(skin)) else renderer.create(entity)
        if (armor.isNotEmpty()) dress(tracker, playerArmor(armor))
        tracker.spawnCondition { canSee(it.uuid()) }
        val loop = AnimationModifier(0, 0, AnimationIterator.Type.LOOP)
        tracker.animate(GUARD, loop)
        val closer: () -> Unit = { runCatching { tracker.close() } }
        closer
    } catch (e: Throwable) {
        logger.warn("Could not pose the Hall statue with BetterModel: ${e.message}")
        null
    }

    /**
     * REQ-296: BetterModel's limb model only draws armor for real players (its armor comes from a Profiled
     * render source, which an entity source never is), so swap each limb bone's item mapper for one that
     * draws the same skin piece with the guild's armor, then redraw. When the skin finishes loading,
     * BetterModel redraws through these mappers too.
     */
    private fun dress(tracker: EntityTracker, armor: PlayerArmor) {
        val limbs = PlayerLimb.entries.associateBy { it.itemMapper }
        tracker.bones().forEach { bone ->
            val limb = limbs[bone.itemMapper] ?: return@forEach
            bone.itemMapper = ArmoredLimb(limb, armor)
            bone.updateItem { true }
        }
    }

    private class ArmoredLimb(private val limb: PlayerLimb, private val armor: PlayerArmor) : BoneItemMapper {
        override fun transform(): PlatformItemTransform = limb.transform
        override fun apply(context: BoneRenderContext, stack: TransformedItemStack): TransformedItemStack =
            limb.skinMapper.apply(context.skin(), armor)
    }

    companion object {
        const val LIMB = "prestige"
        const val GUARD = "guard"

        private fun item(piece: HallArmorPiece?): ArmorItem? = piece?.let { HallModelArmor.layer(it) }
            ?.let { ArmorItem(it.tint, it.type, it.trim, it.palette) }

        fun playerArmor(armor: Map<HallArmorPart, HallArmorPiece>): PlayerArmor {
            val helmet = item(armor[HallArmorPart.HEAD])
            val chest = item(armor[HallArmorPart.CHEST])
            val legs = item(armor[HallArmorPart.LEGS])
            val boots = item(armor[HallArmorPart.FEET])
            return object : PlayerArmor {
                override fun helmet(): ArmorItem? = helmet
                override fun chestplate(): ArmorItem? = chest
                override fun leggings(): ArmorItem? = legs
                override fun boots(): ArmorItem? = boots
            }
        }

        fun orNull(): HallStatuePose? =
            if (Bukkit.getPluginManager().isPluginEnabled("BetterModel")) HallStatuePose() else null
    }
}
