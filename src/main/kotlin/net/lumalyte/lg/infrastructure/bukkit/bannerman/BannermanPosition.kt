package net.lumalyte.lg.infrastructure.bukkit.bannerman

import org.bukkit.Location

/**
 * Pure position math for the bannerman display. Kept free of Bukkit state so
 * the offset logic is trivially unit-testable (same pattern as [BannermanVisibility]).
 *
 * The display uses the HEAD item-display transform, so the banner renders as the
 * full-size banner block centered on the display point — like a banner worn in the
 * helmet slot. That means the position is a simple vertical drop from the eye to the
 * head (the banner extends upward from there); there is no horizontal offset.
 */
object BannermanPosition {

    /**
     * How far below the eye the display sits. Player eye height is 1.62; the head box
     * spans ~1.37–1.87, so this drops the banner's pivot to roughly the base of the head.
     * TUNE IN GAME if the HEAD transform's model pivot differs — it's the one constant
     * that controls whether the banner rides too high or too low.
     */
    private const val HEAD_OFFSET_Y = -0.3

    /**
     * @param eye the player's eye location
     * @return where the banner display should sit: centered on the head, just below
     *         the eye so the full-size banner block extends up from the head.
     */
    fun headPosition(eye: Location): Location = eye.clone().add(0.0, HEAD_OFFSET_Y, 0.0)
}
