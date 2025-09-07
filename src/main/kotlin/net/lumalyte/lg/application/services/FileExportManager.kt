package net.lumalyte.lg.application.services

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists

/**
 * Secure file export manager with rate limiting and temporary file management.
 * Ensures server stability and security for CSV exports.
 */
class FileExportManager(
    private val pluginDataFolder: File,
    private val csvExportService: CsvExportService,
    private val discordCsvService: DiscordCsvService? = null
) {

    private val logger = LoggerFactory.getLogger(FileExportManager::class.java)

    // Rate limiting: max exports per player per time window
    private val rateLimitMap = ConcurrentHashMap<UUID, MutableList<Long>>()
    private val MAX_EXPORTS_PER_HOUR = 5
    private val RATE_LIMIT_WINDOW_MS = TimeUnit.HOURS.toMillis(1)

    // Active exports tracking
    private val activeExports = ConcurrentHashMap<String, ExportJob>()

    // Temporary files settings
    private val tempDir = File(pluginDataFolder, "temp_exports").apply { mkdirs() }
    private val FILE_EXPIRY_MS = TimeUnit.MINUTES.toMillis(15) // 15 minutes
    private val MAX_FILE_SIZE_BYTES = 1024 * 1024 * 5 // 5MB limit

    data class ExportJob(
        val playerId: UUID,
        val fileName: String,
        val tempFile: Path,
        val createdAt: Long,
        val fileSize: Long
    )

    /**
     * Export transaction history to CSV file
     * Uses Discord if configured, otherwise falls back to Minecraft books
     */
    fun exportTransactionHistoryAsync(
        player: Player,
        transactions: List<net.lumalyte.lg.domain.entities.BankTransaction>,
        guildName: String,
        callback: (ExportResult) -> Unit
    ) {
        // Try Discord delivery first if available
        if (discordCsvService?.isConfigured() == true) {
            exportViaDiscord(player, transactions, guildName, callback, true)
            return
        }

        // Fall back to Minecraft books
        exportViaBooks(player, transactions, guildName, callback, true)
    }

    /**
     * Export member contributions
     */
    fun exportMemberContributionsAsync(
        player: Player,
        contributions: List<net.lumalyte.lg.domain.entities.MemberContribution>,
        guildName: String,
        callback: (ExportResult) -> Unit
    ) {
        // Try Discord delivery first if available
        if (discordCsvService?.isConfigured() == true) {
            exportViaDiscord(player, contributions, guildName, callback, false)
            return
        }

        // Fall back to Minecraft books
        exportViaBooks(player, contributions, guildName, callback, false)
    }

    /**
     * Export via Discord webhook
     */
    private inline fun <reified T> exportViaDiscord(
        player: Player,
        data: List<T>,
        guildName: String,
        crossinline callback: (ExportResult) -> Unit,
        isTransactions: Boolean
    ) {
        if (!checkRateLimit(player.uniqueId)) {
            callback(ExportResult.RateLimited("Too many exports. Please wait before requesting another export."))
            return
        }

        when (T::class) {
            net.lumalyte.lg.domain.entities.BankTransaction::class -> {
                discordCsvService?.sendTransactionCsvAsync(
                    player,
                    data as List<net.lumalyte.lg.domain.entities.BankTransaction>,
                    guildName
                ) { result ->
                    result.fold(
                        { successMessage -> callback(ExportResult.DiscordSuccess(successMessage)) },
                        { error ->
                            logger.error("Discord export failed, falling back to books", error)
                            exportViaBooks(player, data, guildName, callback, isTransactions)
                        }
                    )
                }
            }
            net.lumalyte.lg.domain.entities.MemberContribution::class -> {
                discordCsvService?.sendContributionsCsvAsync(
                    player,
                    data as List<net.lumalyte.lg.domain.entities.MemberContribution>,
                    guildName
                ) { result ->
                    result.fold(
                        { successMessage -> callback(ExportResult.DiscordSuccess(successMessage)) },
                        { error ->
                            logger.error("Discord export failed, falling back to books", error)
                            exportViaBooks(player, data, guildName, callback, isTransactions)
                        }
                    )
                }
            }
        }
    }

    /**
     * Export via Minecraft books (fallback method)
     */
    private inline fun <reified T> exportViaBooks(
        player: Player,
        data: List<T>,
        guildName: String,
        crossinline callback: (ExportResult) -> Unit,
        isTransactions: Boolean
    ) {
        if (!checkRateLimit(player.uniqueId)) {
            callback(ExportResult.RateLimited("Too many exports. Please wait before requesting another export."))
            return
        }

        // Generate CSV content
        val csvContent = when (T::class) {
            net.lumalyte.lg.domain.entities.BankTransaction::class ->
                csvExportService.generateTransactionHistoryCsv(data as List<net.lumalyte.lg.domain.entities.BankTransaction>)
            net.lumalyte.lg.domain.entities.MemberContribution::class ->
                csvExportService.generateMemberContributionsCsv(data as List<net.lumalyte.lg.domain.entities.MemberContribution>)
            else -> throw IllegalArgumentException("Unsupported data type for CSV export")
        }

        if (!csvExportService.validateCsvSize(csvContent, MAX_FILE_SIZE_BYTES)) {
            callback(ExportResult.FileTooLarge("Export data too large. Please filter your data or contact an administrator."))
            return
        }

        // Create unique filename
        val fileName = when (T::class) {
            net.lumalyte.lg.domain.entities.BankTransaction::class ->
                "guild_transactions_${guildName}_${System.currentTimeMillis()}.csv"
            net.lumalyte.lg.domain.entities.MemberContribution::class ->
                "guild_contributions_${guildName}_${System.currentTimeMillis()}.csv"
            else -> "guild_export_${guildName}_${System.currentTimeMillis()}.csv"
        }
        val tempFile = tempDir.toPath().resolve(fileName)

        // Start async export
        val jobId = UUID.randomUUID().toString()
        activeExports[jobId] = ExportJob(
            playerId = player.uniqueId,
            fileName = fileName,
            tempFile = tempFile,
            createdAt = System.currentTimeMillis(),
            fileSize = csvContent.toByteArray().size.toLong()
        )

        // Run file writing asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("BellClaims")!!, Runnable {
            try {
                Files.write(tempFile, csvContent.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
                val exportType = when (T::class) {
                    net.lumalyte.lg.domain.entities.BankTransaction::class -> "transaction history"
                    net.lumalyte.lg.domain.entities.MemberContribution::class -> "member contributions"
                    else -> "data"
                }
                logger.info("Successfully exported $exportType for player ${player.name} to $fileName")

                // Schedule cleanup
                scheduleFileCleanup(tempFile)

                // Run callback on main thread
                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("BellClaims")!!, Runnable {
                    callback(ExportResult.Success(fileName, csvContent.toByteArray().size))
                })

            } catch (e: Exception) {
                val exportType = when (T::class) {
                    net.lumalyte.lg.domain.entities.BankTransaction::class -> "transaction history"
                    net.lumalyte.lg.domain.entities.MemberContribution::class -> "member contributions"
                    else -> "data"
                }
                logger.error("Failed to export $exportType for player ${player.name}", e)

                // Cleanup failed file
                tempFile.deleteIfExists()

                // Run callback on main thread
                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("BellClaims")!!, Runnable {
                    callback(ExportResult.Error("Export failed due to server error. Please try again."))
                })
            } finally {
                activeExports.remove(jobId)
            }
        })
    }

    /**
     * Check if player is within rate limits
     */
    private fun checkRateLimit(playerId: UUID): Boolean {
        val now = System.currentTimeMillis()
        val playerRequests = rateLimitMap.getOrPut(playerId) { mutableListOf() }

        // Remove old requests outside the time window
        playerRequests.removeIf { now - it > RATE_LIMIT_WINDOW_MS }

        // Check if under limit
        if (playerRequests.size >= MAX_EXPORTS_PER_HOUR) {
            return false
        }

        // Add current request
        playerRequests.add(now)
        return true
    }

    /**
     * Schedule cleanup of temporary file
     */
    private fun scheduleFileCleanup(filePath: Path) {
        Bukkit.getScheduler().runTaskLaterAsynchronously(
            Bukkit.getPluginManager().getPlugin("BellClaims")!!,
            Runnable {
                try {
                    filePath.deleteIfExists()
                    logger.debug("Cleaned up temporary export file: $filePath")
                } catch (e: Exception) {
                    logger.warn("Failed to cleanup temporary export file: $filePath", e)
                }
            },
            FILE_EXPIRY_MS / 50 // Convert to ticks (20 ticks = 1 second)
        )
    }

    /**
     * Clean up old temporary files on startup
     */
    fun cleanupOldFiles() {
        try {
            val now = System.currentTimeMillis()
            tempDir.listFiles()?.forEach { file ->
                if (now - file.lastModified() > FILE_EXPIRY_MS) {
                    file.delete()
                    logger.debug("Cleaned up old temporary file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to cleanup old temporary files", e)
        }
    }

    /**
     * Get active exports for a player
     */
    fun getActiveExports(playerId: UUID): List<String> {
        return activeExports.values
            .filter { it.playerId == playerId }
            .map { it.fileName }
    }

    /**
     * Cancel active export
     */
    fun cancelExport(playerId: UUID, fileName: String): Boolean {
        val job = activeExports.values.find { it.playerId == playerId && it.fileName == fileName }
        return if (job != null) {
            try {
                job.tempFile.deleteIfExists()
                activeExports.entries.removeIf { it.value == job }
                true
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }

    /**
     * Export result types
     */
    sealed class ExportResult {
        data class Success(val fileName: String, val fileSize: Int) : ExportResult()
        data class DiscordSuccess(val message: String) : ExportResult()
        data class Error(val message: String) : ExportResult()
        data class RateLimited(val message: String) : ExportResult()
        data class FileTooLarge(val message: String) : ExportResult()
    }
}
