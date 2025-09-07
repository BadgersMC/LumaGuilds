package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.GuildBanner
import java.util.UUID

/**
 * Service interface for managing guild banner customization.
 * 
 * This service handles:
 * - Guild banner submission and storage
 * - Banner retrieval and display
 * - Simple validation of banner data
 * - Integration with the existing guild banner system
 */
interface GuildBannerService {

    /**
     * Sets a custom banner for a guild.
     *
     * @param guildId The ID of the guild.
     * @param submitterId The ID of the player setting the banner.
     * @param bannerData The banner design data.
     * @param name Optional name for the banner design.
     * @return true if successful, false otherwise.
     */
    fun setGuildBanner(
        guildId: UUID, 
        submitterId: UUID, 
        bannerData: BannerDesignData, 
        name: String? = null
    ): Boolean

    /**
     * Gets the current banner for a guild.
     *
     * @param guildId The ID of the guild.
     * @return The guild's banner, or null if none set.
     */
    fun getGuildBanner(guildId: UUID): GuildBanner?

    /**
     * Removes the custom banner from a guild.
     *
     * @param guildId The ID of the guild.
     * @param actorId The ID of the player removing the banner.
     * @return true if successful, false otherwise.
     */
    fun removeGuildBanner(guildId: UUID, actorId: UUID): Boolean

    /**
     * Checks if a guild has permission to set banners.
     *
     * @param guildId The ID of the guild.
     * @return true if the guild can set banners, false otherwise.
     */
    fun canSetBanners(guildId: UUID): Boolean

    /**
     * Gets all banners for a guild (if they want to save multiple designs).
     *
     * @param guildId The ID of the guild.
     * @return List of saved banner designs.
     */
    fun getGuildBanners(guildId: UUID): List<GuildBanner>
}

/**
 * Represents banner design data submitted by a guild.
 */
data class BannerDesignData(
    val baseColor: BannerColor,
    val patterns: List<BannerPattern> = emptyList()
) {
    /**
     * Validates the banner design data.
     */
    fun isValid(): Boolean {
        return patterns.size <= 6 && // Minecraft banner limit
               patterns.all { it.isValid() }
    }
}

/**
 * Represents a banner pattern.
 */
data class BannerPattern(
    val type: String, // e.g., "STRIPE_TOP", "CROSS", "BORDER"
    val color: BannerColor
) {
    fun isValid(): Boolean = color.isValid()
}

/**
 * Represents a banner color.
 */
enum class BannerColor(val displayName: String, val hexCode: String, val materialName: String) {
    WHITE("White", "#FFFFFF", "WHITE"),
    ORANGE("Orange", "#FF8F00", "ORANGE"),
    MAGENTA("Magenta", "#C74EBD", "MAGENTA"),
    LIGHT_BLUE("Light Blue", "#3AAFD9", "LIGHT_BLUE"),
    YELLOW("Yellow", "#FED83D", "YELLOW"),
    LIME("Lime", "#80C71F", "LIME"),
    PINK("Pink", "#F38BAA", "PINK"),
    GRAY("Gray", "#474F52", "GRAY"),
    LIGHT_GRAY("Light Gray", "#9D9D97", "LIGHT_GRAY"),
    CYAN("Cyan", "#169C9C", "CYAN"),
    PURPLE("Purple", "#9932CC", "PURPLE"),
    BLUE("Blue", "#3C44AA", "BLUE"),
    BROWN("Brown", "#825432", "BROWN"),
    GREEN("Green", "#5E7C16", "GREEN"),
    RED("Red", "#B02E26", "RED"),
    BLACK("Black", "#1D1C21", "BLACK");

    fun isValid(): Boolean = true
}
