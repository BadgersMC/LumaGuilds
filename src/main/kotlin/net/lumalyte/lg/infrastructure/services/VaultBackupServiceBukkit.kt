package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.GuildVaultRepository
import net.lumalyte.lg.application.services.VaultBackup
import net.lumalyte.lg.application.services.VaultBackupService
import net.lumalyte.lg.application.services.VaultInventoryManager
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Bukkit implementation of VaultBackupService.
 * Stores backups in memory with periodic database snapshots.
 */
class VaultBackupServiceBukkit(
    private val plugin: JavaPlugin,
    private val vaultRepository: GuildVaultRepository,
    private val vaultInventoryManager: VaultInventoryManager
) : VaultBackupService {

    private val logger = LoggerFactory.getLogger(VaultBackupServiceBukkit::class.java)

    // In-memory backup storage: guildId -> List of backups
    private val backups = ConcurrentHashMap<UUID, MutableList<VaultBackup>>()

    // Backup data: backupId -> serialized inventory data
    private val backupData = ConcurrentHashMap<String, String>()

    private var autoBackupTaskId: Int? = null

    override fun createBackup(guildId: UUID, reason: String): String? {
        try {
            // Get current vault state from repository
            val vaultSlots = vaultRepository.getVaultInventory(guildId)
            val goldBalance = vaultRepository.getGoldBalance(guildId)

            // Generate unique backup ID
            val backupId = "${guildId}-${System.currentTimeMillis()}"

            // Serialize inventory data (Map<Int, ItemStack>)
            val serializedData = serializeVaultData(vaultSlots, goldBalance)

            // Count non-null items
            val itemCount = vaultSlots.size

            // Create backup metadata
            val backup = VaultBackup(
                backupId = backupId,
                guildId = guildId,
                timestamp = Instant.now(),
                reason = reason,
                itemCount = itemCount
            )

            // Store backup
            backups.computeIfAbsent(guildId) { mutableListOf() }.add(backup)
            backupData[backupId] = serializedData

            logger.info("Created vault backup: $backupId for guild $guildId (reason: $reason, items: $itemCount)")
            return backupId

        } catch (e: Exception) {
            logger.error("Failed to create vault backup for guild $guildId", e)
            return null
        }
    }

    override fun restoreBackup(guildId: UUID, backupId: String, restoredBy: UUID): Boolean {
        try {
            // Find the backup
            val backup = backups[guildId]?.find { it.backupId == backupId } ?: run {
                logger.warn("Backup $backupId not found for guild $guildId")
                return false
            }

            val serializedData = backupData[backupId] ?: run {
                logger.warn("Backup data not found for backup $backupId")
                return false
            }

            // Deserialize backup data
            val (restoredSlots, restoredGold) = deserializeVaultData(serializedData)

            // Save to database
            val success = vaultRepository.saveVaultInventory(guildId, restoredSlots) &&
                         vaultRepository.setGoldBalance(guildId, restoredGold)

            if (success) {
                // Clear cache to force reload from database
                vaultInventoryManager.clearCache(guildId)

                logger.info("Restored vault backup: $backupId for guild $guildId by admin $restoredBy")

                // Create a backup of the restore point
                createBackup(guildId, "post-restore-$backupId")
            }

            return success

        } catch (e: Exception) {
            logger.error("Failed to restore vault backup $backupId for guild $guildId", e)
            return false
        }
    }

    override fun listBackups(guildId: UUID): List<VaultBackup> {
        return backups[guildId]?.toList() ?: emptyList()
    }

    override fun cleanOldBackups(retentionDays: Int): Int {
        val cutoffDate = Instant.now().minus(retentionDays.toLong(), ChronoUnit.DAYS)
        var deletedCount = 0

        backups.forEach { (guildId, backupList) ->
            val toRemove = backupList.filter { it.timestamp.isBefore(cutoffDate) }

            toRemove.forEach { backup ->
                backupList.remove(backup)
                backupData.remove(backup.backupId)
                deletedCount++
            }
        }

        if (deletedCount > 0) {
            logger.info("Cleaned $deletedCount old vault backups (retention: $retentionDays days)")
        }

        return deletedCount
    }

    override fun startAutoBackup(intervalMinutes: Long) {
        stopAutoBackup() // Stop any existing task

        val intervalTicks = intervalMinutes * 60 * 20 // Convert minutes to ticks (20 ticks/second)

        autoBackupTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            try {
                logger.info("Running automatic vault backups...")
                val guilds = vaultRepository.getAllGuildIds()
                var backedUp = 0

                guilds.forEach { guildId ->
                    if (createBackup(guildId, "auto") != null) {
                        backedUp++
                    }
                }

                logger.info("Auto-backup complete: $backedUp vaults backed up")

                // Clean old backups (keep 7 days)
                cleanOldBackups(7)

            } catch (e: Exception) {
                logger.error("Error during auto-backup", e)
            }
        }, intervalTicks, intervalTicks).taskId

        logger.info("Started automatic vault backups (interval: $intervalMinutes minutes)")
    }

    override fun stopAutoBackup() {
        autoBackupTaskId?.let { taskId ->
            Bukkit.getScheduler().cancelTask(taskId)
            autoBackupTaskId = null
            logger.info("Stopped automatic vault backups")
        }
    }

    /**
     * Serializes vault data (slots + gold balance) to a JSON-like format with Base64 items.
     * Format: "goldBalance|slot:base64Item,slot:base64Item,..."
     */
    private fun serializeVaultData(slots: Map<Int, org.bukkit.inventory.ItemStack>, goldBalance: Long): String {
        val itemsEncoded = slots.entries.joinToString(",") { (slot, item) ->
            val itemBase64 = java.util.Base64.getEncoder().encodeToString(item.serializeAsBytes())
            "$slot:$itemBase64"
        }
        return "$goldBalance|$itemsEncoded"
    }

    /**
     * Deserializes vault data from the format produced by serializeVaultData.
     * Returns Pair of (slots map, gold balance)
     */
    private fun deserializeVaultData(data: String): Pair<Map<Int, org.bukkit.inventory.ItemStack>, Long> {
        val parts = data.split("|", limit = 2)
        val goldBalance = parts[0].toLong()

        val slots = mutableMapOf<Int, org.bukkit.inventory.ItemStack>()
        if (parts.size > 1 && parts[1].isNotEmpty()) {
            parts[1].split(",").forEach { entry ->
                val (slotStr, itemBase64) = entry.split(":", limit = 2)
                val slot = slotStr.toInt()
                val item = org.bukkit.inventory.ItemStack.deserializeBytes(
                    java.util.Base64.getDecoder().decode(itemBase64)
                )
                slots[slot] = item
            }
        }

        return Pair(slots, goldBalance)
    }
}
