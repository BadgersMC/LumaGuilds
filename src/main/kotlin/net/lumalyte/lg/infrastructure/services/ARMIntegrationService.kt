package net.lumalyte.lg.infrastructure.services

import org.bukkit.Bukkit
import org.bukkit.Location
import org.slf4j.LoggerFactory

/**
 * Service for integrating with EnthusiaMarket (EM) plugin.
 * Provides soft dependency checks to prevent vault exploitation in shop containers.
 */
class ARMIntegrationService {

    private val logger = LoggerFactory.getLogger(ARMIntegrationService::class.java)

    init {
        checkAvailability()
    }

    /**
     * Check if EnthusiaMarket plugin is available
     */
    private fun checkAvailability() {
        try {
            Class.forName("net.badgersmc.em.api.ShopGuildLookup")
            logger.info("EnthusiaMarket integration enabled")
            logger.info("Vault placement in shop containers will be blocked to prevent raid immunity exploits")
        } catch (e: ClassNotFoundException) {
            logger.info("EnthusiaMarket not found - vault shop protection disabled")
        } catch (e: Exception) {
            logger.warn("Failed to initialize EnthusiaMarket integration: ${e.message}")
            logger.warn("Vault shop protection disabled - vaults can be placed in shop containers")
        }
    }

    /**
     * Check if a location is inside a shop container.
     * Returns false if EnthusiaMarket is not available or on error.
     *
     * @param location The location to check
     * @return True if the location is a shop container, false otherwise
     */
    fun isInShopRegion(location: Location): Boolean {
        return try {
            val lookupClass = Class.forName("net.badgersmc.em.api.ShopGuildLookup")
            val lookup = Bukkit.getServicesManager().load(lookupClass)

            if (lookup != null) {
                val isShopContainerMethod = lookupClass.getMethod("isShopContainer", Location::class.java)
                isShopContainerMethod.invoke(lookup, location) as? Boolean ?: false
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if EnthusiaMarket integration is available
     */
    fun isEnabled(): Boolean {
        return try {
            Class.forName("net.badgersmc.em.api.ShopGuildLookup")
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Refresh availability (call after plugin reload)
     */
    fun refresh() {
        checkAvailability()
    }
}