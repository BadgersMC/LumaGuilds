package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.GuildBanner
import net.lumalyte.lg.domain.values.BannerDesignData
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
