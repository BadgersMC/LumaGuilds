package net.lumalyte.lg.infrastructure.services.hall

import kr.toxicity.model.api.BetterModel
import kr.toxicity.model.api.animation.AnimationIterator
import kr.toxicity.model.api.animation.AnimationModifier
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter
import kr.toxicity.model.api.profile.ModelProfile
import kr.toxicity.model.api.tracker.EntityTracker
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
     * Wraps [statue] in the limb model wearing [skin]'s skin, seen only by players [canSee] accepts, looping
     * the Guard pose (REQ-283: the only pose). Returns a closer, or null if the model is missing (the plain
     * statue then stays visible).
     */
    fun apply(statue: LivingEntity, canSee: (UUID) -> Boolean, skin: UUID?): (() -> Unit)? = try {
        val renderer = BetterModel.limb(LIMB).orElse(null) ?: return null
        val entity = BukkitAdapter.adapt(statue)
        val tracker: EntityTracker = if (skin != null) renderer.create(entity, ModelProfile.of(skin)) else renderer.create(entity)
        tracker.spawnCondition { canSee(it.uuid()) }
        val loop = AnimationModifier(0, 0, AnimationIterator.Type.LOOP)
        tracker.animate(GUARD, loop)
        val closer: () -> Unit = { runCatching { tracker.close() } }
        closer
    } catch (e: Throwable) {
        logger.warn("Could not pose the Hall statue with BetterModel: ${e.message}")
        null
    }

    companion object {
        const val LIMB = "prestige"
        const val GUARD = "guard"


        fun orNull(): HallStatuePose? =
            if (Bukkit.getPluginManager().isPluginEnabled("BetterModel")) HallStatuePose() else null
    }
}
