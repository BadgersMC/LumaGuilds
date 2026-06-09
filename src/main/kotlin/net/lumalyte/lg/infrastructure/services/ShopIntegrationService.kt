package net.lumalyte.lg.infrastructure.services

import net.badgersmc.em.api.ShopGuildLookup
import org.bukkit.Bukkit
import org.bukkit.Location
import org.slf4j.LoggerFactory

/**
 * Soft integration with EnthusiaMarket's shop system, used to prevent vault
 * exploitation: vaults must not be placeable inside shop containers (raid-immunity
 * exploit). Replaces the former AdvancedRegionMarket (ARM) integration — EnthusiaMarket
 * now owns shops, so we ask it whether a location is a registered shop container.
 *
 * EnthusiaMarket registers [ShopGuildLookup] in Bukkit's ServicesManager at enable.
 * Resolution is lazy + cached so it works regardless of plugin load order; if EM is
 * absent the service fails open (no protection) exactly as the ARM version did.
 */
class ShopIntegrationService {

    private val logger = LoggerFactory.getLogger(ShopIntegrationService::class.java)
    private var cached: ShopGuildLookup? = null

    private fun lookup(): ShopGuildLookup? {
        cached?.let { return it }
        return try {
            Bukkit.getServicesManager().load(ShopGuildLookup::class.java)?.also {
                cached = it
                logger.info("EnthusiaMarket ShopGuildLookup integration enabled — " +
                        "vault placement in shop containers will be blocked")
            }
        } catch (e: Exception) {
            logger.warn("Failed to load EnthusiaMarket ShopGuildLookup: ${e.message}")
            null
        }
    }

    /**
     * Check if a location is a registered EnthusiaMarket shop container.
     * Returns false (fail-open) if EnthusiaMarket is unavailable or on error.
     */
    fun isShopLocation(location: Location): Boolean {
        val lk = lookup() ?: return false
        return try {
            lk.isShopContainer(location)
        } catch (e: Exception) {
            logger.error("Error checking shop container at ${location.world?.name} " +
                    "(${location.blockX}, ${location.blockY}, ${location.blockZ}): ${e.message}", e)
            false
        }
    }

    /** Whether the EnthusiaMarket integration is currently available. */
    fun isEnabled(): Boolean = lookup() != null

    /** Drop the cached lookup so the next call re-resolves (call after a plugin reload). */
    fun refresh() {
        cached = null
    }
}
