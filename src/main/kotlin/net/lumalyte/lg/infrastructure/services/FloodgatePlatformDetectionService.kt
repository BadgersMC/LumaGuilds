package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.PlatformDetectionService
import org.bukkit.entity.Player
import java.util.logging.Logger

/**
 * Implementation of PlatformDetectionService using Floodgate API
 */
class FloodgatePlatformDetectionService(
    private val logger: Logger
) : PlatformDetectionService {

    override fun isBedrockPlayer(player: Player): Boolean {
        return try {
            // Check if Floodgate is available and player is Bedrock
            if (!isFloodgateAvailable()) {
                logger.fine("Floodgate not available for player ${player.name}")
                return false
            }

            // Use Floodgate API to check if player is Bedrock
            // Note: Using instance-based API as of Floodgate 2.0
            val floodgateApi = org.geysermc.floodgate.api.FloodgateApi.getInstance()
            val isBedrock = floodgateApi.isFloodgatePlayer(player.uniqueId)

            logger.fine("Platform detection for ${player.name}: isBedrock=$isBedrock")
            return isBedrock

        } catch (e: NoClassDefFoundError) {
            logger.warning("Floodgate classes not found for player ${player.name}: ${e.message}")
            false
        } catch (e: IllegalStateException) {
            logger.warning("Floodgate API not properly initialized for player ${player.name}: ${e.message}")
            false
        } catch (e: Exception) {
            // Service operation - catching all exceptions to prevent service failure
            logger.warning("Unexpected error checking Bedrock status for player ${player.name}: ${e.message}")
            logger.warning("Stack trace: ${e.stackTraceToString()}")
            false
        }
    }

    override fun isFloodgateAvailable(): Boolean {
        return try {
            // Check if Floodgate API is available
            val floodgateApi = org.geysermc.floodgate.api.FloodgateApi.getInstance()
            val available = floodgateApi != null
            logger.fine("Floodgate availability check: $available")
            return available
        } catch (e: NoClassDefFoundError) {
            logger.warning("Floodgate classes not found: ${e.message}")
            false
        } catch (e: IllegalStateException) {
            logger.warning("Floodgate API not properly initialized: ${e.message}")
            false
        } catch (e: Exception) {
            // Service operation - catching all exceptions to prevent service failure
            logger.warning("Unexpected error checking Floodgate availability: ${e.message}")
            logger.warning("Stack trace: ${e.stackTraceToString()}")
            false
        }
    }

    override fun isCumulusAvailable(): Boolean {
        return try {
            // Check if Cumulus API classes are available
            Class.forName("org.geysermc.cumulus.Form")
            logger.fine("Cumulus availability check: true")
            true
        } catch (e: ClassNotFoundException) {
            logger.warning("Cumulus API classes not found: ${e.message}")
            false
        } catch (e: NoClassDefFoundError) {
            logger.warning("Cumulus classes not found: ${e.message}")
            false
        } catch (e: Exception) {
            // Service operation - catching all exceptions to prevent service failure
            logger.warning("Unexpected error checking Cumulus availability: ${e.message}")
            logger.warning("Stack trace: ${e.stackTraceToString()}")
            false
        }
    }
}
