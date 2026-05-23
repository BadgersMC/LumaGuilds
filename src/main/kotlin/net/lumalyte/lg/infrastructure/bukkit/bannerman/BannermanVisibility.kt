package net.lumalyte.lg.infrastructure.bukkit.bannerman

/**
 * Decides whether a guild bannerman display should be visible on a player's back.
 *
 * Pure function: takes only what affects the decision, returns the decision.
 * Live Bukkit state (elytra slot, potion effects) is queried by the caller and passed in,
 * keeping this logic trivially unit-testable.
 */
object BannermanVisibility {
    /**
     * @return true if the banner should be shown to onlookers.
     */
    fun shouldShow(hasElytra: Boolean, hasInvisibility: Boolean): Boolean =
        !hasElytra && !hasInvisibility
}
