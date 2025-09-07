package net.lumalyte.lg.domain.entities

import net.lumalyte.lg.application.services.BannerDesignData
import java.time.Instant
import java.util.UUID

/**
 * Represents a custom banner design for a guild.
 *
 * @property id The unique identifier for the banner.
 * @property guildId The ID of the guild that owns this banner.
 * @property name Optional name for the banner design.
 * @property designData The actual banner design (colors, patterns, etc.).
 * @property submittedBy The ID of the player who created the banner.
 * @property createdAt The timestamp when the banner was created.
 * @property isActive Whether this banner is currently active for the guild.
 */
data class GuildBanner(
    val id: UUID,
    val guildId: UUID,
    val name: String? = null,
    val designData: BannerDesignData,
    val submittedBy: UUID,
    val createdAt: Instant,
    val isActive: Boolean = true
) {
    init {
        require(designData.isValid()) { "Banner design data must be valid." }
    }
}
