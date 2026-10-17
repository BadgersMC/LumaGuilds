package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.services.BankAutomationService
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.slf4j.LoggerFactory

/**
 * Periodic bank automation driver (REQ-009): interest accrual + audit-log pruning.
 *
 * Interest stays on the Bukkit main thread because the settlement path can update a live
 * inventory. Audit pruning is database-only and runs asynchronously so a busy SQLite writer
 * can never stall the server thread long enough to trip the watchdog.
 */
class BankInterestScheduler(
    private val plugin: Plugin,
    private val bankAutomationService: BankAutomationService
) {
    private val logger = LoggerFactory.getLogger(BankInterestScheduler::class.java)

    private var scheduledTask: BukkitRunnable? = null
    private var pruneTask: BukkitRunnable? = null

    private val runIntervalTicks = 5L * 60L * 20L
    // Retention is measured in days; pruning hourly is ample and avoids competing with
    // the five-minute interest sweep. The first prune is deliberately offset by one minute.
    private val pruneInitialDelayTicks = runIntervalTicks + (60L * 20L)
    private val pruneIntervalTicks = 60L * 60L * 20L

    fun start() {
        if (scheduledTask != null || pruneTask != null) return

        scheduledTask = object : BukkitRunnable() {
            override fun run() {
                try {
                    val credited = bankAutomationService.accrueInterest()
                    if (credited > 0) {
                        logger.info("Bank interest accrued for $credited guild(s)")
                    }
                } catch (e: DatabaseOperationException) {
                    logger.error("Database error running bank interest accrual", e)
                } catch (e: IllegalStateException) {
                    logger.error("Service error running bank interest accrual", e)
                }
            }
        }
        scheduledTask?.runTaskTimer(plugin, runIntervalTicks, runIntervalTicks)

        pruneTask = object : BukkitRunnable() {
            override fun run() {
                try {
                    val pruned = bankAutomationService.pruneAuditLogs()
                    if (pruned > 0) {
                        logger.info("Pruned $pruned expired bank audit entr${if (pruned == 1) "y" else "ies"}")
                    }
                } catch (e: DatabaseOperationException) {
                    logger.error("Database error pruning bank audit logs", e)
                } catch (e: IllegalStateException) {
                    logger.error("Service error pruning bank audit logs", e)
                }
            }
        }
        pruneTask?.runTaskTimerAsynchronously(plugin, pruneInitialDelayTicks, pruneIntervalTicks)

        logger.info("Bank interest scheduler started (interest every 5 minutes; audit pruning async hourly)")
    }

    fun stop() {
        scheduledTask?.cancel()
        pruneTask?.cancel()
        scheduledTask = null
        pruneTask = null
        logger.info("Bank interest scheduler stopped")
    }
}
