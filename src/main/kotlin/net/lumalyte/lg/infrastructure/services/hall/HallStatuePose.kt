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
     * Wraps [statue] in the limb model wearing [skin]'s skin, seen only by [viewer], looping the pose
     * named [pose] or Guard when the model has no such animation. Returns a closer, or null if the
     * model is missing (the plain statue then stays visible).
     */
    fun apply(statue: LivingEntity, viewer: UUID, skin: UUID?, pose: String): (() -> Unit)? = try {
        val renderer = BetterModel.limb(LIMB).orElse(null) ?: return null
        val entity = BukkitAdapter.adapt(statue)
        val tracker: EntityTracker = if (skin != null) renderer.create(entity, ModelProfile.of(skin)) else renderer.create(entity)
        tracker.spawnCondition { it.uuid() == viewer }
        val loop = AnimationModifier(0, 0, AnimationIterator.Type.LOOP)
        val wanted = animationFor(pose)
        if (!tracker.animate(wanted, loop) && wanted != GUARD) tracker.animate(GUARD, loop)
        val closer: () -> Unit = { runCatching { tracker.close() } }
        closer
    } catch (e: Throwable) {
        logger.warn("Could not pose the Hall statue with BetterModel: ${e.message}")
        null
    }

    companion object {
        const val LIMB = "prestige"
        const val GUARD = "guard"

        /** Animation names in the prestige model are the pose names in lower case: guard, victory. */
        fun animationFor(pose: String): String = pose.trim().lowercase().replace(' ', '_').ifEmpty { GUARD }

        fun orNull(): HallStatuePose? =
            if (Bukkit.getPluginManager().isPluginEnabled("BetterModel")) HallStatuePose() else null
    }
}
