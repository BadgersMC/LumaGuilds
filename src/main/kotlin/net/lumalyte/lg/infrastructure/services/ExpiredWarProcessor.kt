package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.WarService
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.slf4j.LoggerFactory

/**
 * Scheduler for processing expired wars and war declarations.
 * Runs periodically to check for and end wars that have exceeded their duration.
 */
class ExpiredWarProcessor(
    private val plugin: Plugin,
    private val warService: WarService
) {

    private val logger = LoggerFactory.getLogger(ExpiredWarProcessor::class.java)

    private var scheduledTask: BukkitRunnable? = null

    /**
     * Starts the periodic processor.
     * Runs every hour to check for expired wars.
     */
    fun startProcessor() {
        // Run every hour (72000 ticks = 60 minutes * 60 seconds * 20 ticks/second)
        val periodTicks = 72000L

        logger.info("Starting expired war processor (runs every hour)")

        scheduledTask = object : BukkitRunnable() {
            override fun run() {
                try {
                    val processedCount = warService.processExpiredWars()
                    if (processedCount > 0) {
                        logger.info("Processed $processedCount expired wars/declarations")
                    }
                } catch (e: Exception) {
                    // Background task - catching all exceptions to ensure continuation
                    logger.error("Error processing expired wars", e)
                }
            }
        }

        // Run first check after 5 minutes (6000 ticks), then every hour
        scheduledTask?.runTaskTimer(plugin, 6000L, periodTicks)
    }

    /**
     * Stops the processor.
     */
    fun stopProcessor() {
        scheduledTask?.cancel()
        scheduledTask = null
        logger.info("Expired war processor stopped")
    }

    /**
     * Manually triggers expired war processing.
     * Useful for testing or admin commands.
     */
    fun triggerManualProcessing(): Int {
        return try {
            val processedCount = warService.processExpiredWars()
            logger.info("Manual expired war processing: $processedCount items processed")
            processedCount
        } catch (e: Exception) {
            logger.error("Error during manual expired war processing", e)
            0
        }
    }
}
