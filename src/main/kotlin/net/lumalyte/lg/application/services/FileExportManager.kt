package net.lumalyte.lg.application.services

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import org.bukkit.plugin.Plugin
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists
import net.lumalyte.lg.domain.entities.BankTransaction
import net.lumalyte.lg.domain.entities.GuildChest
import net.lumalyte.lg.domain.entities.GuildChestAccessLog
import net.lumalyte.lg.domain.entities.MemberContribution

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

    sealed class ExportResult {
        data class Success(val fileName: String, val fileSize: Int) : ExportResult()
        data class DiscordSuccess(val message: String) : ExportResult()
        data class Error(val message: String) : ExportResult()
        data class RateLimited(val message: String) : ExportResult()
        data class FileTooLarge(val message: String) : ExportResult()
    }

    /**
     * Export item banking data to CSV file
     * Uses Discord if configured, otherwise falls back to Minecraft books
     */
    fun exportItemBankingDataAsync(
        player: Player,
        guildId: UUID,
        guildName: String,
        chests: List<GuildChest>,
        accessLogs: List<GuildChestAccessLog>,
        totalItems: Int,
        totalValue: Double,
        callback: (ExportResult) -> Unit
    ) {
        val plugin = Bukkit.getPluginManager().getPlugin("LumaGuilds")
            ?: return callback(ExportResult.Error("Plugin not found"))

        if (!isConfigured()) {
            return callback(ExportResult.Error("Export service not configured"))
        }

        if (!checkRateLimit(player.uniqueId)) {
            return callback(ExportResult.RateLimited("Too many exports in the last hour. Try again later."))
        }

        val csvContent = csvExportService.generateItemBankingReportCsv(
            guildId = guildId,
            guildName = guildName,
            chests = chests,
            accessLogs = accessLogs,
            totalItems = totalItems,
            totalValue = totalValue
        )

        if (csvContent.toByteArray().size > MAX_FILE_SIZE_BYTES) {
            return callback(ExportResult.FileTooLarge("File too large (${csvContent.toByteArray().size} bytes). Maximum allowed: $MAX_FILE_SIZE_BYTES bytes."))
        }

        val fileName = "guild_item_banking_${guildName}_${System.currentTimeMillis()}.csv"
        val tempFile = tempDir.toPath().resolve(fileName)

        activeExports[fileName] = ExportJob(
            playerId = player.uniqueId,
            fileName = fileName,
            tempFile = tempFile,
            createdAt = System.currentTimeMillis(),
            fileSize = csvContent.toByteArray().size.toLong()
        )

        // Run file writing asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                Files.write(tempFile, csvContent.toByteArray(), StandardOpenOption.CREATE_NEW)

                // Use Minecraft book export
                sendToMinecraftBook(player, csvContent, "Item Banking Report - $guildName")
                tempFile.deleteIfExists()
                activeExports.remove(fileName)
                callback(ExportResult.Success(fileName, csvContent.toByteArray().size))
            } catch (e: Exception) {
                logger.error("Failed to export item banking data", e)
                tempFile.deleteIfExists()
                activeExports.remove(fileName)
                callback(ExportResult.Error("Failed to export item banking data: ${e.message}"))
            }
        })
    }

    /**
     * Export transaction history to CSV file
     * Uses Discord if configured, otherwise falls back to Minecraft books
     */
    fun exportTransactionHistoryAsync(
        player: Player,
        transactions: List<BankTransaction>,
        guildName: String,
        callback: (ExportResult) -> Unit
    ) {
        val plugin = Bukkit.getPluginManager().getPlugin("LumaGuilds")
            ?: return callback(ExportResult.Error("Plugin not found"))

        if (!isConfigured()) {
            return callback(ExportResult.Error("Export service not configured"))
        }

        if (!checkRateLimit(player.uniqueId)) {
            return callback(ExportResult.RateLimited("Too many exports in the last hour. Try again later."))
        }

        val csvContent = csvExportService.generateTransactionHistoryCsv(transactions)

        if (csvContent.toByteArray().size > MAX_FILE_SIZE_BYTES) {
            return callback(ExportResult.FileTooLarge("File too large (${csvContent.toByteArray().size} bytes). Maximum allowed: $MAX_FILE_SIZE_BYTES bytes."))
        }

        val fileName = "guild_transactions_${guildName}_${System.currentTimeMillis()}.csv"
        val tempFile = tempDir.toPath().resolve(fileName)

        activeExports[fileName] = ExportJob(
            playerId = player.uniqueId,
            fileName = fileName,
            tempFile = tempFile,
            createdAt = System.currentTimeMillis(),
            fileSize = csvContent.toByteArray().size.toLong()
        )

        // Run file writing asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                Files.write(tempFile, csvContent.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
                logger.info("Successfully exported transaction history for player ${player.name} to $fileName")

                // Schedule cleanup
                scheduleFileCleanup(tempFile)

                // Try Discord first if available
                if (discordCsvService != null) {
                    discordCsvService.sendTransactionCsvAsync(player, transactions, guildName) { result ->
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            // Discord service handles its own success/failure
                            // If Discord fails, fall back to local file
                            callback(ExportResult.Success(fileName, csvContent.toByteArray().size))
                        })
                    }
                } else {
                    // Fall back to Minecraft book
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        callback(ExportResult.Success(fileName, csvContent.toByteArray().size))
                    })
                }

            } catch (e: Exception) {
                logger.error("Failed to export transaction history for player ${player.name}", e)
                tempFile.toFile().delete()
                activeExports.remove(fileName)

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    callback(ExportResult.Error("Export failed: ${e.message}"))
                })
            }
        })
    }

    fun exportMemberContributionsAsync(
        player: Player,
        contributions: List<MemberContribution>,
        guildName: String,
        callback: (ExportResult) -> Unit
    ) {
        val plugin = Bukkit.getPluginManager().getPlugin("LumaGuilds")
            ?: return callback(ExportResult.Error("Plugin not found"))

        if (!isConfigured()) {
            return callback(ExportResult.Error("Export service not configured"))
        }

        if (!checkRateLimit(player.uniqueId)) {
            return callback(ExportResult.RateLimited("Too many exports in the last hour. Try again later."))
        }

        val csvContent = csvExportService.generateMemberContributionsCsv(contributions)

        if (csvContent.toByteArray().size > MAX_FILE_SIZE_BYTES) {
            return callback(ExportResult.FileTooLarge("File too large (${csvContent.toByteArray().size} bytes). Maximum allowed: $MAX_FILE_SIZE_BYTES bytes."))
        }

        val fileName = "guild_contributions_${guildName}_${System.currentTimeMillis()}.csv"
        val tempFile = tempDir.toPath().resolve(fileName)

        activeExports[fileName] = ExportJob(
            playerId = player.uniqueId,
            fileName = fileName,
            tempFile = tempFile,
            createdAt = System.currentTimeMillis(),
            fileSize = csvContent.toByteArray().size.toLong()
        )

        // Run file writing asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                Files.write(tempFile, csvContent.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
                logger.info("Successfully exported contributions for player ${player.name} to $fileName")

                // Schedule cleanup
                scheduleFileCleanup(tempFile)

                // Try Discord first if available
                if (discordCsvService != null) {
                    discordCsvService.sendContributionsCsvAsync(player, contributions, guildName) { result ->
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            // Discord service handles its own success/failure
                            // If Discord fails, fall back to local file
                            callback(ExportResult.Success(fileName, csvContent.toByteArray().size))
                        })
                    }
                } else {
                    // Fall back to Minecraft book
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        callback(ExportResult.Success(fileName, csvContent.toByteArray().size))
                    })
                }

            } catch (e: Exception) {
                logger.error("Failed to export contributions for player ${player.name}", e)
                tempFile.toFile().delete()
                activeExports.remove(fileName)

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    callback(ExportResult.Error("Export failed: ${e.message}"))
                })
            }
        })
    }

    private fun isConfigured(): Boolean {
        return csvExportService != null
    }

    private fun checkRateLimit(playerId: UUID): Boolean {
        val now = System.currentTimeMillis()
        val playerRequests = rateLimitMap.computeIfAbsent(playerId) { mutableListOf() }

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

    fun getActiveExports(playerId: UUID): List<String> {
        return activeExports.values
            .filter { it.playerId == playerId }
            .map { it.fileName }
    }

    fun cancelExport(playerId: UUID, fileName: String): Boolean {
        val job = activeExports[fileName] ?: return false
        if (job.playerId != playerId) return false // Only allow canceling own exports

        activeExports.remove(fileName)
        job.tempFile.toFile().delete()
        return true
    }

    private fun scheduleFileCleanup(file: Path) {
        val plugin = Bukkit.getPluginManager().getPlugin("LumaGuilds") ?: return

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            try {
                Files.deleteIfExists(file)
                logger.info("Cleaned up expired export file: ${file.fileName}")
            } catch (e: Exception) {
                logger.warn("Failed to cleanup export file: ${file.fileName}", e)
            }
        }, (FILE_EXPIRY_MS / 50).toLong()) // Convert to ticks (20 ticks per second)
    }

    fun cleanupOldFiles() {
        val now = System.currentTimeMillis()
        val filesToRemove = mutableListOf<Path>()

        // Find old files in temp directory
        tempDir.listFiles()?.forEach { file ->
            if (now - file.lastModified() > FILE_EXPIRY_MS) {
                filesToRemove.add(file.toPath())
            }
        }

        // Remove expired files
        filesToRemove.forEach { file ->
            try {
                Files.deleteIfExists(file)
                logger.info("Cleaned up expired file: ${file.fileName}")
            } catch (e: Exception) {
                logger.warn("Failed to cleanup file: ${file.fileName}", e)
            }
        }
    }

    /**
     * Send CSV content to a Minecraft book for the player
     */
    private fun sendToMinecraftBook(player: Player, csvContent: String, title: String) {
        try {
            // Split CSV into pages (approximately 14 lines per page for a book)
            val lines = csvContent.lines()
            val pages = mutableListOf<String>()

            var currentPage = ""
            for (line in lines) {
                if (currentPage.split("\n").size >= 13) { // Leave room for page number
                    pages.add(currentPage)
                    currentPage = ""
                }
                currentPage += line + "\n"
            }
            if (currentPage.isNotEmpty()) {
                pages.add(currentPage)
            }

            // Create a written book
            val book = ItemStack(Material.WRITTEN_BOOK)
            val meta = book.itemMeta as BookMeta

            meta.title = title.take(32) // Book titles are limited to 32 characters
            meta.author = player.name

            // Add pages
            pages.take(50).forEach { page -> // Books are limited to 50 pages
                meta.addPage(page)
            }

            book.itemMeta = meta

            // Give the book to the player
            player.inventory.addItem(book)

        } catch (e: Exception) {
            logger.error("Failed to send CSV to Minecraft book for player ${player.name}", e)
        }
    }
}
