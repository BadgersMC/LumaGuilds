package net.lumalyte.lg.infrastructure.services

import org.bukkit.Bukkit
import org.bukkit.Location
import org.slf4j.LoggerFactory

/**
 * Service for integrating with AdvancedRegionMarket (ARM) plugin.
 * Provides soft dependency checks to prevent vault exploitation in shop regions.
 */
class ARMIntegrationService {

    private val logger = LoggerFactory.getLogger(ARMIntegrationService::class.java)
    private var armEnabled = false
    private var armAdapter: net.alex9849.arm.adapters.ARMVersionAdapter? = null

    init {
        checkARMAvailability()
    }

    /**
     * Check if ARM plugin is available and load the adapter
     */
    private fun checkARMAvailability() {
        try {
            val armPlugin = Bukkit.getPluginManager().getPlugin("AdvancedRegionMarket")

            if (armPlugin != null && armPlugin.isEnabled) {
                // ARM is present - try to get the adapter
                val arm = armPlugin as? net.alex9849.arm.AdvancedRegionMarket

                if (arm != null) {
                    armAdapter = arm.adapterHandler
                    armEnabled = true
                    logger.info("AdvancedRegionMarket integration enabled")
                    logger.info("Vault placement in shop regions will be blocked to prevent raid immunity exploits")
                } else {
                    logger.warn("AdvancedRegionMarket plugin found but wrong type - vault shop protection disabled")
                }
            } else {
                logger.info("AdvancedRegionMarket not found - vault shop protection disabled")
            }
        } catch (e: Exception) {
            logger.warn("Failed to initialize ARM integration: ${e.message}")
            logger.warn("Vault shop protection disabled - vaults can be placed in shop regions")
            armEnabled = false
            armAdapter = null
        }
    }

    /**
     * Check if a location is inside a shop region.
     * Returns false if ARM is not available or on error.
     *
     * @param location The location to check
     * @return True if the location is in a shop region (sold/unsold), false otherwise
     */
    fun isInShopRegion(location: Location): Boolean {
        if (!armEnabled || armAdapter == null) {
            return false
        }

        return try {
            val region = armAdapter?.getRegion(location)
            region != null // If a region exists at this location, it's a shop region
        } catch (e: Exception) {
            // Log error and fail safe (allow placement)
            logger.error("Error checking ARM shop region at ${location.world?.name} " +
                    "(${location.blockX}, ${location.blockY}, ${location.blockZ}): ${e.message}", e)
            false
        }
    }

    /**
     * Check if ARM integration is enabled
     */
    fun isEnabled(): Boolean = armEnabled

    /**
     * Refresh ARM availability (call after plugin reload)
     */
    fun refresh() {
        checkARMAvailability()
    }
}
