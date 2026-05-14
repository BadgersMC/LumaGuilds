package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.ProgressionRepository
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.slf4j.LoggerFactory

/**
 * Periodically prunes rows from the `experience_transactions` table using
 * [ProgressionRepository.deleteOldTransactions].
 *
 * Configured via `progression.transaction_retention_days` and
 * `progression.transaction_cleanup_interval_hours`. Either value set to 0
 * disables the task.
 */
class ExperienceTransactionCleanupScheduler(
    private val plugin: Plugin,
    private val progressionRepository: ProgressionRepository,
    private val retentionDays: Int,
    private val intervalHours: Int
) {
    private val logger = LoggerFactory.getLogger(ExperienceTransactionCleanupScheduler::class.java)
    private var task: BukkitTask? = null

    fun start() {
        if (retentionDays <= 0 || intervalHours <= 0) {
            logger.info("Experience transaction cleanup disabled (retentionDays=$retentionDays, intervalHours=$intervalHours)")
            return
        }

        // 20 ticks per second × 3600 s/h
        val periodTicks = intervalHours.toLong() * 60L * 60L * 20L
        // Delay first run a bit so it doesn't compete with startup work.
        val initialDelayTicks = 20L * 60L * 5L // 5 minutes

        task = object : BukkitRunnable() {
            override fun run() {
                try {
                    val removed = progressionRepository.deleteOldTransactions(retentionDays)
                    if (removed > 0) {
                        logger.info("Pruned $removed experience transaction(s) older than $retentionDays days")
                    }
                } catch (e: Exception) {
                    // Background task — swallow to keep the timer alive.
                    logger.error("Experience transaction cleanup failed", e)
                }
            }
        }.runTaskTimerAsynchronously(plugin, initialDelayTicks, periodTicks)

        logger.info("Experience transaction cleanup scheduled every $intervalHours h, keeping $retentionDays days")
    }

    fun stop() {
        task?.cancel()
        task = null
    }
}
