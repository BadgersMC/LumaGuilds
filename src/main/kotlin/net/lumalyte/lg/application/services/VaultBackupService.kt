package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.VaultInventory
import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.util.UUID

/**
 * Service for backing up and restoring guild vault inventories.
 * Provides automatic periodic backups and manual backup/restore capabilities.
 */
interface VaultBackupService {

    /**
     * Creates a backup of a vault's current state.
     *
     * @param guildId The guild whose vault to backup
     * @param reason Reason for the backup (e.g., "auto", "manual", "pre-operation")
     * @return The backup ID if successful, null otherwise
     */
    fun createBackup(guildId: UUID, reason: String): String?

    /**
     * Restores a vault to a previous backup state.
     *
     * @param guildId The guild whose vault to restore
     * @param backupId The backup ID to restore from
     * @param restoredBy The UUID of the admin performing the restore
     * @return True if restore was successful
     */
    fun restoreBackup(guildId: UUID, backupId: String, restoredBy: UUID): Boolean

    /**
     * Lists all available backups for a guild's vault.
     *
     * @param guildId The guild to list backups for
     * @return List of backup metadata (ID, timestamp, reason)
     */
    fun listBackups(guildId: UUID): List<VaultBackup>

    /**
     * Deletes old backups beyond the retention period.
     *
     * @param retentionDays Number of days to keep backups
     * @return Number of backups deleted
     */
    fun cleanOldBackups(retentionDays: Int): Int

    /**
     * Starts the automatic backup scheduler.
     *
     * @param intervalMinutes How often to backup (in minutes)
     */
    fun startAutoBackup(intervalMinutes: Long)

    /**
     * Stops the automatic backup scheduler.
     */
    fun stopAutoBackup()
}

/**
 * Metadata for a vault backup.
 */
data class VaultBackup(
    val backupId: String,
    val guildId: UUID,
    val timestamp: Instant,
    val reason: String,
    val itemCount: Int
)
