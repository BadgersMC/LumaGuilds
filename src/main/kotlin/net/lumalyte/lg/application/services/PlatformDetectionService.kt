package net.lumalyte.lg.application.services

import org.bukkit.entity.Player

/**
 * Service for detecting whether a player is using Bedrock Edition
 */
interface PlatformDetectionService {
    /**
     * Checks if the given player is using Bedrock Edition
     * @param player The player to check
     * @return true if the player is using Bedrock Edition, false otherwise
     */
    fun isBedrockPlayer(player: Player): Boolean

    /**
     * Checks if Floodgate is available on the server
     * @return true if Floodgate is available, false otherwise
     */
    fun isFloodgateAvailable(): Boolean

    /**
     * Checks if Cumulus API is available for form handling
     * @return true if Cumulus is available, false otherwise
     */
    fun isCumulusAvailable(): Boolean
}
