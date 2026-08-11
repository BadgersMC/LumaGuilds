package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.BankAutomationService
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.slf4j.LoggerFactory

/**
 * Periodic bank automation driver (REQ-009): interest accrual + audit-log pruning.
 *
 * Runs every 5 minutes; each run accrues interest for every guild whose compound
 * period has elapsed (catch-up capped by [BankAutomationService]) and prunes
 * expired audit entries.
 */
class BankInterestScheduler(
    private val plugin: Plugin,
    private val bankAutomationService: BankAutomationService
) {

    private val logger = LoggerFactory.getLogger(BankInterestScheduler::class.java)

    private var scheduledTask: BukkitRunnable? = null

    private val runIntervalTicks = 5L * 60L * 20L // 5 minutes (20 ticks per second)

    /** Starts the periodic scheduler. Safe to call once at plugin enable. */
    fun start() {
        if (scheduledTask != null) return

        scheduledTask = object : BukkitRunnable() {
            override fun run() {
                try {
                    val credited = bankAutomationService.accrueInterest()
                    if (credited > 0) {
                        logger.info("Bank interest accrued for $credited guild(s)")
                    }
                } catch (e: Exception) {
                    logger.error("Error running bank interest accrual", e)
                }

                try {
                    val pruned = bankAutomationService.pruneAuditLogs()
                    if (pruned > 0) {
                        logger.info("Pruned $pruned expired bank audit entr${if (pruned == 1) "y" else "ies"}")
                    }
                } catch (e: Exception) {
                    logger.error("Error pruning bank audit logs", e)
                }
            }
        }

        scheduledTask?.runTaskTimer(plugin, runIntervalTicks, runIntervalTicks)
        logger.info("Bank interest scheduler started (every 5 minutes)")
    }

    /** Stops the periodic scheduler. */
    fun stop() {
        scheduledTask?.cancel()
        scheduledTask = null
        logger.info("Bank interest scheduler stopped")
    }
}
