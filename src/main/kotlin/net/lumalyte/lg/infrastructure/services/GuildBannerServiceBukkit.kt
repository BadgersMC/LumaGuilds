package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.application.persistence.GuildBannerRepository
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.domain.entities.GuildBanner
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * Bukkit implementation of GuildBannerService.
 * 
 * This service allows guilds to create and manage custom banner designs
 * without requiring approval - it runs on the honor system.
 */
class GuildBannerServiceBukkit : GuildBannerService, KoinComponent {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    private val bannerRepository: GuildBannerRepository by inject()
    private val guildRepository: GuildRepository by inject()
    private val permissionResolver: GuildRolePermissionResolverBukkit by inject()

    override fun setGuildBanner(
        guildId: UUID, 
        submitterId: UUID, 
        bannerData: BannerDesignData, 
        name: String?
    ): Boolean {
        try {
            // Validate banner data
            if (!bannerData.isValid()) {
                logger.warn("Invalid banner data submitted by player $submitterId for guild $guildId")
                return false
            }

            // Check if guild exists
            val guild = guildRepository.getById(guildId) ?: run {
                logger.warn("Guild $guildId not found when setting banner")
                return false
            }

            // Check if player has permission to set banners
            if (!canSetBanners(guildId)) {
                logger.warn("Player $submitterId does not have permission to set banners for guild $guildId")
                return false
            }

            // Create the banner
            val banner = GuildBanner(
                id = UUID.randomUUID(),
                guildId = guildId,
                name = name,
                designData = bannerData,
                submittedBy = submitterId,
                createdAt = Instant.now(),
                isActive = true
            )

            // Save to repository
            val success = bannerRepository.save(banner)
            if (success) {
                logger.info("Banner set for guild $guildId by player $submitterId")
            }
            return success

        } catch (e: Exception) {
            // Service operation - catching all exceptions to prevent service failure
            logger.error("Error setting banner for guild $guildId", e)
            return false
        }
    }

    override fun getGuildBanner(guildId: UUID): GuildBanner? {
        return try {
            bannerRepository.getActiveBanner(guildId)
        } catch (e: Exception) {
            // Service operation - catching all exceptions to prevent service failure
            logger.error("Error retrieving banner for guild $guildId", e)
            null
        }
    }

    override fun removeGuildBanner(guildId: UUID, actorId: UUID): Boolean {
        try {
            // Check if player has permission
            if (!canSetBanners(guildId)) {
                logger.warn("Player $actorId does not have permission to remove banners for guild $guildId")
                return false
            }

            val success = bannerRepository.removeActiveBanner(guildId)
            if (success) {
                logger.info("Banner removed for guild $guildId by player $actorId")
            }
            return success

        } catch (e: Exception) {
            // Service operation - catching all exceptions to prevent service failure
            logger.error("Error removing banner for guild $guildId", e)
            return false
        }
    }

    override fun canSetBanners(guildId: UUID): Boolean {
        return try {
            // Check if guild exists and has basic permissions
            val guild = guildRepository.getById(guildId) ?: return false
            
            // For now, any guild member can set banners
            // This could be enhanced with role-based permissions later
            true
        } catch (e: Exception) {
            // Service operation - catching all exceptions to prevent service failure
            logger.error("Error checking banner permissions for guild $guildId", e)
            false
        }
    }

    override fun getGuildBanners(guildId: UUID): List<GuildBanner> {
        return try {
            bannerRepository.getBannersByGuild(guildId)
        } catch (e: Exception) {
            // Service operation - catching all exceptions to prevent service failure
            logger.error("Error retrieving banners for guild $guildId", e)
            emptyList()
        }
    }
}
