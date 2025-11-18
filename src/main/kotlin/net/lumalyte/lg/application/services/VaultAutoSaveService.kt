package net.lumalyte.lg.application.services

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption

/**
 * Manages auto-save and crash detection for the vault system.
 * Runs periodic background tasks to flush pending writes and detects crashes.
 */
class VaultAutoSaveService(
    private val plugin: JavaPlugin,
    private val vaultInventoryManager: VaultInventoryManager,
    private val transactionLogger: net.lumalyte.lg.infrastructure.persistence.guilds.VaultTransactionLogger? = null,
    private val transactionRetentionDays: Int = 30
) {

    private var autoSaveTask: BukkitTask? = null
    private var idleCleanupTask: BukkitTask? = null
    private var archivalTask: BukkitTask? = null
    private val runningMarkerFile: File = File(plugin.dataFolder, ".vault_running")

    /**
     * Starts the auto-save service.
     * This should be called during plugin enable.
     */
    fun start() {
        // Check for crash from previous session
        detectCrash()

        // Create running marker file
        createRunningMarker()

        // Start auto-save task (every 1 second = 20 ticks)
        autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            Runnable { performAutoSave() },
            20L, // Initial delay: 1 second
            20L  // Period: 1 second
        )

        // Start idle cleanup task (every 5 minutes = 6000 ticks)
        idleCleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            Runnable { cleanupIdleSessions() },
            6000L, // Initial delay: 5 minutes
            6000L  // Period: 5 minutes
        )

        // Start transaction log archival task (every 24 hours = 1728000 ticks)
        // Initial delay: 1 hour
        if (transactionLogger != null) {
            archivalTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                Runnable { archiveOldTransactions() },
                72000L,    // Initial delay: 1 hour
                1728000L   // Period: 24 hours
            )
            plugin.logger.info("Transaction log archival enabled (retention: $transactionRetentionDays days)")
        }

        plugin.logger.info("Vault auto-save service started (1s interval)")
    }

    /**
     * Stops the auto-save service.
     * This should be called during plugin disable.
     */
    fun stop() {
        // Cancel background tasks
        autoSaveTask?.cancel()
        idleCleanupTask?.cancel()
        archivalTask?.cancel()

        // Perform final synchronous save
        performBlockingShutdownSave()

        // Remove running marker file
        deleteRunningMarker()

        plugin.logger.info("Vault auto-save service stopped")
    }

    /**
     * Performs auto-save by flushing all pending write buffers.
     * Runs asynchronously every 1 second.
     */
    private fun performAutoSave() {
        try {
            val flushedCount = vaultInventoryManager.flushPendingBuffers()

            if (flushedCount > 0) {
                plugin.logger.fine("Auto-save: Flushed $flushedCount vault buffer(s)")
            }
        } catch (e: Exception) {
            plugin.logger.severe("Error during auto-save: ${e.message}")
            e.printStackTrace()

            // Retry on next cycle (task will continue running)
        }
    }

    /**
     * Cleans up idle viewer sessions.
     * Runs every 5 minutes.
     */
    private fun cleanupIdleSessions() {
        try {
            val cleanedUp = vaultInventoryManager.cleanupIdleSessions(300000) // 5 minutes

            if (cleanedUp > 0) {
                plugin.logger.info("Cleaned up $cleanedUp idle vault session(s)")
            }
        } catch (e: Exception) {
            plugin.logger.severe("Error during idle session cleanup: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Performs a blocking shutdown save.
     * Saves all vaults synchronously before the plugin shuts down.
     */
    private fun performBlockingShutdownSave() {
        plugin.logger.info("Performing blocking shutdown save for all vaults...")

        val startTime = System.currentTimeMillis()
        val stats = vaultInventoryManager.getCacheStats()
        val vaultCount = stats["cached_vaults"] ?: 0

        if (vaultCount == 0) {
            plugin.logger.info("No vaults to save")
            return
        }

        try {
            // Force flush all vaults synchronously
            vaultInventoryManager.forceFlushAll()

            val duration = System.currentTimeMillis() - startTime
            plugin.logger.info("✓ Successfully saved $vaultCount vault(s) in ${duration}ms")
        } catch (e: Exception) {
            plugin.logger.severe("!!! CRITICAL: Failed to save vaults during shutdown !!!")
            plugin.logger.severe("Error: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Creates the running marker file to detect crashes.
     */
    private fun createRunningMarker() {
        try {
            if (!plugin.dataFolder.exists()) {
                plugin.dataFolder.mkdirs()
            }

            Files.write(
                runningMarkerFile.toPath(),
                "Vault system running. Delete this file only if the server is not running.".toByteArray(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            )

            plugin.logger.fine("Created vault running marker file")
        } catch (e: Exception) {
            plugin.logger.warning("Failed to create running marker file: ${e.message}")
        }
    }

    /**
     * Deletes the running marker file on clean shutdown.
     */
    private fun deleteRunningMarker() {
        try {
            if (runningMarkerFile.exists()) {
                runningMarkerFile.delete()
                plugin.logger.fine("Deleted vault running marker file")
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to delete running marker file: ${e.message}")
        }
    }

    /**
     * Detects if the server crashed in the previous session.
     * Checks for the presence of the running marker file.
     */
    private fun detectCrash() {
        if (runningMarkerFile.exists()) {
            plugin.logger.warning("╔════════════════════════════════════════════════════════════╗")
            plugin.logger.warning("║ VAULT CRASH DETECTED                                       ║")
            plugin.logger.warning("║                                                            ║")
            plugin.logger.warning("║ The vault system was not shut down cleanly in the          ║")
            plugin.logger.warning("║ previous session. This may indicate a server crash.        ║")
            plugin.logger.warning("║                                                            ║")
            plugin.logger.warning("║ Vault data has been loaded from the database.              ║")
            plugin.logger.warning("║ Potential data loss window: up to 1 second of changes.     ║")
            plugin.logger.warning("║                                                            ║")
            plugin.logger.warning("║ If valuable items are missing, check transaction logs.     ║")
            plugin.logger.warning("╚════════════════════════════════════════════════════════════╝")

            // Delete the marker so we don't show this warning again
            runningMarkerFile.delete()
        } else {
            plugin.logger.info("No crash detected - vault system starting normally")
        }
    }

    /**
     * Archives old transaction log entries.
     * Deletes transactions older than the configured retention period.
     * Runs every 24 hours.
     */
    private fun archiveOldTransactions() {
        if (transactionLogger == null) {
            return
        }

        try {
            val retentionMillis = transactionRetentionDays * 24L * 60L * 60L * 1000L
            val cutoffTime = System.currentTimeMillis() - retentionMillis

            plugin.logger.info("Starting transaction log archival (retention: $transactionRetentionDays days)...")

            val totalCountBefore = transactionLogger.getTransactionCount()
            val deletedCount = transactionLogger.archiveOldTransactions(cutoffTime)
            val totalCountAfter = transactionLogger.getTransactionCount()

            if (deletedCount > 0) {
                plugin.logger.info("✓ Archived $deletedCount old transaction(s) (${totalCountBefore} → ${totalCountAfter})")
            } else {
                plugin.logger.info("No old transactions to archive")
            }
        } catch (e: Exception) {
            plugin.logger.severe("Error during transaction log archival: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Gets auto-save statistics.
     */
    fun getStats(): Map<String, Any> {
        val stats = mutableMapOf<String, Any>(
            "auto_save_running" to (autoSaveTask != null && !autoSaveTask!!.isCancelled),
            "idle_cleanup_running" to (idleCleanupTask != null && !idleCleanupTask!!.isCancelled),
            "running_marker_exists" to runningMarkerFile.exists()
        )

        if (transactionLogger != null) {
            stats["archival_running"] = archivalTask != null && !archivalTask!!.isCancelled
            stats["transaction_count"] = transactionLogger.getTransactionCount()
            stats["transaction_retention_days"] = transactionRetentionDays
        }

        return stats
    }
}
