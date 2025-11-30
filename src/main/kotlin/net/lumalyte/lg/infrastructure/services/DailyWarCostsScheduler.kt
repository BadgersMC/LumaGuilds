package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.DailyWarCostsService
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.slf4j.LoggerFactory
import java.time.LocalTime

/**
 * Scheduler for running daily war costs application.
 */
class DailyWarCostsScheduler(
    private val plugin: Plugin,
    private val dailyWarCostsService: DailyWarCostsService
) {

    private val logger = LoggerFactory.getLogger(DailyWarCostsScheduler::class.java)

    private var scheduledTask: BukkitRunnable? = null

    /**
     * Starts the daily scheduler.
     * Runs at a fixed time each day (e.g., 2:00 AM server time).
     */
    fun startDailyScheduler() {
        // Calculate delay until next execution (2:00 AM)
        val targetTime = LocalTime.of(2, 0) // 2:00 AM
        val now = LocalTime.now()
        var delayHours = if (now.isBefore(targetTime)) {
            java.time.Duration.between(now, targetTime).toHours()
        } else {
            // Next day
            24 - java.time.Duration.between(targetTime, now).toHours()
        }

        if (delayHours <= 0) delayHours = 24 // Fallback to 24 hours

        val delayTicks = (delayHours * 60 * 60 * 20).toLong() // Convert hours to ticks (20 ticks per second)

        logger.info("Scheduling daily war costs to run in ${delayHours} hours (${delayTicks} ticks)")

        scheduledTask = object : BukkitRunnable() {
            override fun run() {
                try {
                    val affectedGuilds = dailyWarCostsService.applyDailyWarCosts()
                    logger.info("Daily war costs applied to $affectedGuilds guilds")

                    // Reschedule for next day
                    scheduleNextExecution()
                } catch (e: Exception) {
                    // Background task - catching all exceptions to ensure rescheduling
                    logger.error("Error running daily war costs", e)
                    // Still reschedule for next day even if this run failed
                    scheduleNextExecution()
                }
            }
        }

        scheduledTask?.runTaskLater(plugin, delayTicks)
    }

    /**
     * Stops the daily scheduler.
     */
    fun stopDailyScheduler() {
        scheduledTask?.cancel()
        scheduledTask = null
        logger.info("Daily war costs scheduler stopped")
    }

    /**
     * Schedules the next daily execution.
     */
    private fun scheduleNextExecution() {
        // Schedule next execution in 24 hours
        val delayTicks = (24 * 60 * 60 * 20).toLong() // 24 hours in ticks

        scheduledTask = object : BukkitRunnable() {
            override fun run() {
                try {
                    val affectedGuilds = dailyWarCostsService.applyDailyWarCosts()
                    logger.info("Daily war costs applied to $affectedGuilds guilds")

                    // Reschedule for next day
                    scheduleNextExecution()
                } catch (e: Exception) {
                    // Background task - catching all exceptions to ensure rescheduling
                    logger.error("Error running daily war costs", e)
                    // Still reschedule for next day even if this run failed
                    scheduleNextExecution()
                }
            }
        }

        scheduledTask?.runTaskLater(plugin, delayTicks)
    }

    /**
     * Manually triggers the daily war costs application.
     * Useful for testing or admin commands.
     */
    fun triggerManualExecution(): Int {
        return try {
            val affectedGuilds = dailyWarCostsService.applyDailyWarCosts()
            logger.info("Manual daily war costs applied to $affectedGuilds guilds")
            affectedGuilds
        } catch (e: Exception) {
            logger.error("Error running manual daily war costs", e)
            0
        }
    }
}
