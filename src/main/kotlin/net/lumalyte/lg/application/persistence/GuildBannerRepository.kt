package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.GuildBanner
import java.util.UUID

/**
 * Repository for managing guild banners.
 * 
 * This repository handles:
 * - Storing custom banner designs for guilds
 * - Retrieving active and saved banners
 * - Managing banner lifecycle (active/inactive)
 */
interface GuildBannerRepository {

    /**
     * Saves a guild banner.
     *
     * @param banner The banner to save.
     * @return true if successful, false otherwise.
     */
    fun save(banner: GuildBanner): Boolean

    /**
     * Gets the active banner for a guild.
     *
     * @param guildId The ID of the guild.
     * @return The active banner, or null if none exists.
     */
    fun getActiveBanner(guildId: UUID): GuildBanner?

    /**
     * Gets all banners for a guild.
     *
     * @param guildId The ID of the guild.
     * @return List of all banners for the guild.
     */
    fun getBannersByGuild(guildId: UUID): List<GuildBanner>

    /**
     * Gets a specific banner by ID.
     *
     * @param bannerId The ID of the banner.
     * @return The banner, or null if not found.
     */
    fun getById(bannerId: UUID): GuildBanner?

    /**
     * Removes the active banner for a guild.
     *
     * @param guildId The ID of the guild.
     * @return true if successful, false otherwise.
     */
    fun removeActiveBanner(guildId: UUID): Boolean

    /**
     * Deletes a specific banner.
     *
     * @param bannerId The ID of the banner to delete.
     * @return true if successful, false otherwise.
     */
    fun delete(bannerId: UUID): Boolean

    /**
     * Sets a banner as active for a guild.
     *
     * @param guildId The ID of the guild.
     * @param bannerId The ID of the banner to activate.
     * @return true if successful, false otherwise.
     */
    fun setActiveBanner(guildId: UUID, bannerId: UUID): Boolean
}
