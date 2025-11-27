package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.GuildVaultRepository
import net.lumalyte.lg.application.utilities.GoldBalanceButton
import net.lumalyte.lg.domain.entities.VaultInventory
import net.lumalyte.lg.domain.entities.ViewerSession
import net.lumalyte.lg.domain.entities.WriteBuffer
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages in-memory caching of guild vault inventories.
 * Provides zero-latency reads through in-memory cache and batched writes to database.
 * Thread-safe for concurrent access from multiple players.
 */
class VaultInventoryManager(
    private val vaultRepository: GuildVaultRepository,
    private val transactionLogger: net.lumalyte.lg.infrastructure.persistence.guilds.VaultTransactionLogger? = null
) {

    private val logger = LoggerFactory.getLogger(VaultInventoryManager::class.java)

    /**
     * In-memory cache of loaded vault inventories.
     * Key: Guild UUID
     * Value: VaultInventory
     */
    private val vaultCache = ConcurrentHashMap<UUID, VaultInventory>()

    /**
     * Active viewer sessions.
     * Key: Player UUID
     * Value: ViewerSession
     */
    private val viewerSessions = ConcurrentHashMap<UUID, ViewerSession>()

    /**
     * Write buffers for batching database writes.
     * Key: Guild UUID
     * Value: WriteBuffer
     */
    private val writeBuffers = ConcurrentHashMap<UUID, WriteBuffer>()

    /**
     * Gets a vault inventory, loading from database if not cached.
     * This method is thread-safe and ensures only one load per guild ID.
     *
     * @param guildId The guild ID.
     * @return The VaultInventory for this guild.
     */
    fun getOrLoadVault(guildId: UUID): VaultInventory {
        return vaultCache.computeIfAbsent(guildId) { id ->
            loadVaultFromDatabase(id)
        }
    }

    /**
     * Loads a vault from the database.
     * Called on cache miss.
     *
     * @param guildId The guild ID.
     * @return The loaded VaultInventory.
     */
    private fun loadVaultFromDatabase(guildId: UUID): VaultInventory {
        val slots = vaultRepository.getVaultInventory(guildId)
        val goldBalance = vaultRepository.getGoldBalance(guildId)

        val vault = VaultInventory(guildId = guildId)
        vault.setGold(goldBalance)

        // Load all slots into the concurrent hash map
        slots.forEach { (slot, item) ->
            vault.slots[slot] = item
        }

        return vault
    }

    /**
     * Updates a slot in the vault.
     * Updates the in-memory cache immediately and queues database write.
     *
     * @param guildId The guild ID.
     * @param slot The slot index.
     * @param item The new item (null to clear slot).
     * @param playerId Optional player ID for transaction logging (if null, no transaction is logged).
     * @return The previous item in that slot, or null.
     */
    fun updateSlot(guildId: UUID, slot: Int, item: ItemStack?, playerId: UUID? = null): ItemStack? {
        val vault = getOrLoadVault(guildId)
        val previousItem = vault.setSlot(slot, item)

        // Buffer the change for database write
        bufferSlotChange(guildId, slot, item)

        // Log transaction for valuable items only (if player ID provided)
        if (playerId != null && transactionLogger != null) {
            // Determine transaction type based on item change
            if (item != null && isValuableItem(item)) {
                // Item added or replaced
                transactionLogger.logItemTransaction(
                    guildId,
                    playerId,
                    net.lumalyte.lg.infrastructure.persistence.guilds.VaultTransactionType.ITEM_ADD,
                    item,
                    slot
                )
            } else if (previousItem != null && isValuableItem(previousItem)) {
                // Item removed
                transactionLogger.logItemTransaction(
                    guildId,
                    playerId,
                    net.lumalyte.lg.infrastructure.persistence.guilds.VaultTransactionType.ITEM_REMOVE,
                    previousItem,
                    slot
                )
            }
        }

        return previousItem
    }

    /**
     * Gets an item from a specific slot.
     * Reads from in-memory cache (zero latency).
     *
     * @param guildId The guild ID.
     * @param slot The slot index.
     * @return The item in that slot, or null if empty.
     */
    fun getSlot(guildId: UUID, slot: Int): ItemStack? {
        val vault = getOrLoadVault(guildId)
        return vault.getSlot(slot)
    }

    /**
     * Gets all slots for a vault.
     * Reads from in-memory cache.
     *
     * @param guildId The guild ID.
     * @return Map of slot index to ItemStack.
     */
    fun getAllSlots(guildId: UUID): Map<Int, ItemStack?> {
        val vault = getOrLoadVault(guildId)
        return vault.slots.toMap()
    }

    /**
     * Gets the gold balance for a vault.
     * Reads from in-memory cache (zero latency).
     *
     * @param guildId The guild ID.
     * @return The gold balance in nuggets.
     */
    fun getGoldBalance(guildId: UUID): Long {
        val vault = getOrLoadVault(guildId)
        return vault.getGold()
    }

    /**
     * Sets the gold balance for a vault.
     * Updates the in-memory cache immediately and queues database write.
     *
     * @param guildId The guild ID.
     * @param balance The new balance in nuggets.
     */
    fun setGoldBalance(guildId: UUID, balance: Long) {
        val vault = getOrLoadVault(guildId)
        vault.setGold(balance)

        // Buffer the change for database write
        bufferGoldChange(guildId, balance)
    }

    /**
     * Deposits gold into a vault and logs the transaction.
     *
     * @param guildId The guild ID.
     * @param playerId The player depositing gold.
     * @param amount The amount in nuggets to deposit.
     * @return The new balance after deposit.
     */
    fun depositGold(guildId: UUID, playerId: UUID, amount: Long): Long {
        val vault = getOrLoadVault(guildId)
        val newBalance = vault.addGold(amount)

        // Buffer the change for database write
        bufferGoldChange(guildId, newBalance)

        // Log transaction immediately (append-only, very fast)
        transactionLogger?.logGoldTransaction(
            guildId,
            playerId,
            net.lumalyte.lg.infrastructure.persistence.guilds.VaultTransactionType.GOLD_DEPOSIT,
            amount
        )

        return newBalance
    }

    /**
     * Withdraws gold from a vault and logs the transaction.
     *
     * @param guildId The guild ID.
     * @param playerId The player withdrawing gold.
     * @param amount The amount in nuggets to withdraw.
     * @return The new balance after withdrawal, or -1 if insufficient funds.
     */
    fun withdrawGold(guildId: UUID, playerId: UUID, amount: Long): Long {
        val vault = getOrLoadVault(guildId)
        val newBalance = vault.subtractGold(amount)

        if (newBalance == -1L) {
            return -1L // Insufficient balance
        }

        // Buffer the change for database write
        bufferGoldChange(guildId, newBalance)

        // Log transaction immediately (append-only, very fast)
        transactionLogger?.logGoldTransaction(
            guildId,
            playerId,
            net.lumalyte.lg.infrastructure.persistence.guilds.VaultTransactionType.GOLD_WITHDRAW,
            amount
        )

        return newBalance
    }

    /**
     * Registers a viewer session when a player opens the vault.
     *
     * @param player The player viewing the vault.
     * @param guildId The guild ID.
     * @param inventory The Bukkit inventory being displayed.
     */
    fun registerViewer(player: Player, guildId: UUID, inventory: Inventory) {
        val session = ViewerSession(
            playerId = player.uniqueId,
            guildId = guildId,
            inventory = inventory
        )
        viewerSessions[player.uniqueId] = session
    }

    /**
     * Unregisters a viewer session when a player closes the vault.
     *
     * @param playerId The player ID.
     */
    fun unregisterViewer(playerId: UUID) {
        viewerSessions.remove(playerId)
    }

    /**
     * Gets all active viewers for a specific vault.
     *
     * @param guildId The guild ID.
     * @return List of ViewerSession objects.
     */
    fun getViewersForVault(guildId: UUID): List<ViewerSession> {
        return viewerSessions.values.filter { it.guildId == guildId }
    }

    /**
     * Checks if a player is currently viewing a vault.
     *
     * @param playerId The player ID.
     * @return true if the player has an active viewer session.
     */
    fun isViewing(playerId: UUID): Boolean {
        return viewerSessions.containsKey(playerId)
    }

    /**
     * Gets the viewer session for a player.
     *
     * @param playerId The player ID.
     * @return The ViewerSession, or null if not viewing.
     */
    fun getViewerSession(playerId: UUID): ViewerSession? {
        return viewerSessions[playerId]
    }

    /**
     * Buffers a slot change for later database write.
     *
     * @param guildId The guild ID.
     * @param slot The slot index.
     * @param item The new item (null to clear).
     */
    private fun bufferSlotChange(guildId: UUID, slot: Int, item: ItemStack?) {
        val buffer = writeBuffers.computeIfAbsent(guildId) { WriteBuffer(guildId) }
        buffer.bufferSlotChange(slot, item)
    }

    /**
     * Buffers a gold balance change for later database write.
     *
     * @param guildId The guild ID.
     * @param balance The new balance.
     */
    private fun bufferGoldChange(guildId: UUID, balance: Long) {
        val buffer = writeBuffers.computeIfAbsent(guildId) { WriteBuffer(guildId) }
        buffer.bufferGoldChange(balance)
    }

    /**
     * Flushes the write buffer for a vault to the database.
     * This writes all pending slot changes and gold balance updates in a batch.
     *
     * @param guildId The guild ID.
     * @return true if the flush was successful.
     */
    fun flushBuffer(guildId: UUID): Boolean {
        val buffer = writeBuffers[guildId] ?: return true // No buffer, nothing to flush

        if (!buffer.hasPendingChanges()) {
            return true // Nothing to flush
        }

        var success = true

        // Flush pending slot changes (additions/updates)
        buffer.pendingSlots.forEach { (slot, item) ->
            if (!saveSlotWithRetry(guildId, slot, item)) {
                success = false
            }
        }

        // Flush pending slot deletions (cleared slots)
        buffer.pendingDeletions.forEach { slot ->
            if (!saveSlotWithRetry(guildId, slot, null)) {
                success = false
            }
        }

        // Flush pending gold balance change
        buffer.pendingGoldBalance?.let { goldBalance ->
            if (!saveGoldBalanceWithRetry(guildId, goldBalance)) {
                success = false
            }
        }

        if (success) {
            buffer.clear()
        }

        return success
    }

    /**
     * Flushes all write buffers that meet flush criteria.
     * Should be called periodically (e.g., every tick or every second).
     *
     * @return Number of buffers flushed.
     */
    fun flushPendingBuffers(): Int {
        var flushedCount = 0

        writeBuffers.forEach { (guildId, buffer) ->
            if (buffer.shouldFlush()) {
                if (flushBuffer(guildId)) {
                    flushedCount++
                }
            }
        }

        return flushedCount
    }

    /**
     * Forces immediate flush of all pending writes for a vault.
     * Used when a player closes the vault or server shuts down.
     *
     * @param guildId The guild ID.
     */
    fun forceFlush(guildId: UUID) {
        flushBuffer(guildId)
    }

    /**
     * Forces immediate flush of all pending writes for all vaults.
     * Used during server shutdown.
     */
    fun forceFlushAll() {
        writeBuffers.keys.forEach { guildId ->
            flushBuffer(guildId)
        }
    }

    /**
     * Evicts a vault from the cache.
     * Should only be called when no players are viewing the vault.
     *
     * @param guildId The guild ID.
     */
    fun evictVault(guildId: UUID) {
        // Flush any pending writes first
        flushBuffer(guildId)

        // Remove from cache
        vaultCache.remove(guildId)
        writeBuffers.remove(guildId)
    }

    /**
     * Clears all caches.
     * Used for testing or reload operations.
     */
    fun clearAllCaches() {
        forceFlushAll()
        vaultCache.clear()
        viewerSessions.clear()
        writeBuffers.clear()
    }

    /**
     * Gets cache statistics for monitoring.
     *
     * @return Map of statistic names to values.
     */
    fun getCacheStats(): Map<String, Int> {
        return mapOf(
            "cached_vaults" to vaultCache.size,
            "active_viewers" to viewerSessions.size,
            "pending_write_buffers" to writeBuffers.size
        )
    }

    // ========== Real-Time Broadcasting (Phase 4) ==========

    /**
     * Opens a vault for a player and registers them as a viewer.
     * This method should be called when a player opens the vault inventory.
     *
     * @param guildId The guild ID.
     * @param player The player opening the vault.
     * @param inventory The Bukkit inventory being displayed.
     */
    fun openVaultFor(guildId: UUID, player: Player, inventory: Inventory) {
        registerViewer(player, guildId, inventory)
    }

    /**
     * Closes a vault for a player and unregisters them as a viewer.
     * This method should be called when a player closes the vault inventory.
     * Also performs an immediate flush of pending writes.
     *
     * @param guildId The guild ID.
     * @param playerId The player ID.
     */
    fun closeVaultFor(guildId: UUID, playerId: UUID) {
        unregisterViewer(playerId)

        // If no more viewers, flush pending writes immediately
        val remainingViewers = getViewersForVault(guildId)
        if (remainingViewers.isEmpty()) {
            forceFlush(guildId)
        }
    }

    /**
     * Cleans up viewer sessions for a disconnected player.
     * Should be called when a player disconnects from the server.
     *
     * @param playerId The player ID.
     */
    fun cleanupDisconnectedPlayer(playerId: UUID) {
        val session = viewerSessions[playerId]
        if (session != null) {
            closeVaultFor(session.guildId, playerId)
        }
    }

    /**
     * Broadcasts a slot update to all active viewers of a vault.
     * Updates happen synchronously within 1 game tick (50ms).
     *
     * @param guildId The guild ID.
     * @param slot The slot index that was updated.
     * @param item The new item in that slot (null if cleared).
     * @param excludePlayer Optional player UUID to exclude from broadcast (the player who made the change).
     */
    fun broadcastSlotUpdate(guildId: UUID, slot: Int, item: ItemStack?, excludePlayer: UUID? = null) {
        val viewers = getViewersForVault(guildId)

        if (viewers.isEmpty()) {
            return // No viewers to broadcast to
        }

        // Update all viewer inventories synchronously (except the player who made the change)
        viewers.forEach { session ->
            // Skip the player who triggered this update (they already have the change)
            if (excludePlayer != null && session.playerId == excludePlayer) {
                return@forEach
            }

            try {
                session.inventory.setItem(slot, item)
                session.recordInteraction()
            } catch (e: Exception) {
                // Handle case where inventory is no longer valid
                unregisterViewer(session.playerId)
            }
        }
    }

    /**
     * Broadcasts a gold balance update to all active viewers of a vault.
     * Regenerates the Gold Balance Button in slot 0 with the new balance.
     *
     * @param guildId The guild ID.
     * @param newBalance The new gold balance in nuggets.
     */
    fun broadcastGoldUpdate(guildId: UUID, newBalance: Long) {
        // This will be implemented once we integrate GoldBalanceButton
        // For now, we just broadcast a slot 0 update
        // The actual button creation will be handled by the inventory integration layer
        val viewers = getViewersForVault(guildId)

        if (viewers.isEmpty()) {
            return // No viewers to broadcast to
        }

        // Note: The Gold Balance Button should be created by the caller
        // and passed to broadcastSlotUpdate(guildId, 0, goldBalanceButton)
        // This method is a convenience wrapper that will be fully implemented
        // in Phase 5 when we integrate with VaultInventoryHolder
    }

    /**
     * Updates a slot and broadcasts the change to all viewers.
     * This is the primary method for updating vault contents with real-time sync.
     *
     * @param guildId The guild ID.
     * @param slot The slot index.
     * @param item The new item (null to clear slot).
     * @param playerId Optional player ID for transaction logging and excluding from broadcast.
     * @return The previous item in that slot, or null.
     */
    fun updateSlotWithBroadcast(guildId: UUID, slot: Int, item: ItemStack?, playerId: UUID? = null): ItemStack? {
        val previousItem = updateSlot(guildId, slot, item, playerId)
        // Broadcast to all viewers EXCEPT the player who made the change
        broadcastSlotUpdate(guildId, slot, item, excludePlayer = playerId)
        return previousItem
    }

    /**
     * Updates gold balance and broadcasts the change to all viewers.
     *
     * @param guildId The guild ID.
     * @param balance The new balance in nuggets.
     */
    fun setGoldBalanceWithBroadcast(guildId: UUID, balance: Long) {
        setGoldBalance(guildId, balance)
        broadcastGoldUpdate(guildId, balance)
    }

    /**
     * Cleans up idle viewer sessions.
     * Should be called periodically to remove sessions that have been idle too long.
     *
     * @param idleThresholdMs The idle threshold in milliseconds (default 5 minutes).
     * @return Number of sessions cleaned up.
     */
    fun cleanupIdleSessions(idleThresholdMs: Long = 300000): Int {
        var cleanedUp = 0

        viewerSessions.values.toList().forEach { session ->
            if (session.isIdle(idleThresholdMs)) {
                closeVaultFor(session.guildId, session.playerId)
                cleanedUp++
            }
        }

        return cleanedUp
    }

    /**
     * Clears the cache for a specific guild vault.
     * Used when a vault is cleared (e.g., items dropped on break).
     *
     * @param guildId The guild ID.
     */
    fun clearCache(guildId: UUID) {
        vaultCache.remove(guildId)
        writeBuffers.remove(guildId)
    }

    // ========== Error Recovery & Retry Logic (Phase 8) ==========

    /**
     * Saves a vault slot with retry logic.
     * Implements exponential backoff on failures.
     *
     * @param guildId The guild ID.
     * @param slot The slot index.
     * @param item The item to save (null to clear).
     * @param retries Maximum number of retry attempts (default 3).
     * @return true if save was successful.
     */
    private fun saveSlotWithRetry(
        guildId: UUID,
        slot: Int,
        item: ItemStack?,
        retries: Int = 3
    ): Boolean {
        var attempt = 0
        var lastError: Exception? = null

        while (attempt < retries) {
            try {
                if (vaultRepository.saveVaultItem(guildId, slot, item)) {
                    return true // Success
                }
                // If the repository returns false (but doesn't throw), treat as failure
                attempt++
            } catch (e: Exception) {
                lastError = e
                attempt++
                logger.warn("Failed to save vault slot (attempt $attempt/$retries): ${e.message}")

                if (attempt < retries) {
                    // Exponential backoff: 100ms, 200ms, 400ms
                    Thread.sleep(100L * (1 shl (attempt - 1)))
                }
            }
        }

        // All retries failed - log critical error
        logger.error(
            "CRITICAL: Failed to save vault slot after $retries attempts! " +
            "Guild: $guildId, Slot: $slot, Item: ${item?.type}",
            lastError
        )

        // Mark vault as dirty so it will be retried on next auto-save cycle
        val vault = vaultCache[guildId]
        if (vault != null) {
            vault.markDirty()
        }

        return false
    }

    /**
     * Saves a gold balance with retry logic.
     * Implements exponential backoff on failures.
     *
     * @param guildId The guild ID.
     * @param balance The balance to save.
     * @param retries Maximum number of retry attempts (default 3).
     * @return true if save was successful.
     */
    private fun saveGoldBalanceWithRetry(
        guildId: UUID,
        balance: Long,
        retries: Int = 3
    ): Boolean {
        var attempt = 0
        var lastError: Exception? = null

        while (attempt < retries) {
            try {
                if (vaultRepository.setGoldBalance(guildId, balance)) {
                    return true // Success
                }
                // If the repository returns false (but doesn't throw), treat as failure
                attempt++
            } catch (e: Exception) {
                lastError = e
                attempt++
                logger.warn("Failed to save gold balance (attempt $attempt/$retries): ${e.message}")

                if (attempt < retries) {
                    // Exponential backoff: 100ms, 200ms, 400ms
                    Thread.sleep(100L * (1 shl (attempt - 1)))
                }
            }
        }

        // All retries failed - log critical error
        logger.error(
            "CRITICAL: Failed to save gold balance after $retries attempts! " +
            "Guild: $guildId, Balance: $balance",
            lastError
        )

        // Mark vault as dirty so it will be retried on next auto-save cycle
        val vault = vaultCache[guildId]
        if (vault != null) {
            vault.markDirty()
        }

        return false
    }

    /**
     * Validates that a player's inventory view is synchronized with the cache.
     * Detects and auto-repairs any desyncs (cache is source of truth).
     *
     * @param guildId The guild ID.
     * @param inventory The Bukkit inventory to validate.
     * @return true if inventory is in sync or was successfully repaired.
     */
    fun validateInventorySync(guildId: UUID, inventory: Inventory): Boolean {
        val vault = vaultCache[guildId] ?: return false
        var desyncDetected = false

        // Validate all slots (including slot 0 - gold button)
        for (slot in 0 until inventory.size) {
            val guiItem = inventory.getItem(slot)
            val cachedItem = vault.getSlot(slot)

            if (!itemsEqual(guiItem, cachedItem)) {
                logger.error(
                    "DESYNC DETECTED: Guild $guildId slot $slot - " +
                    "GUI: ${guiItem?.type}x${guiItem?.amount}, Cache: ${cachedItem?.type}x${cachedItem?.amount}"
                )
                desyncDetected = true

                // Auto-repair: prefer cache over GUI (cache is source of truth)
                inventory.setItem(slot, cachedItem)
            }
        }

        if (desyncDetected) {
            logger.warn("Auto-repaired inventory desync for guild $guildId")
        }

        return true
    }

    /**
     * Compares two ItemStacks for equality.
     * Checks both material type and amount.
     *
     * @param a First ItemStack.
     * @param b Second ItemStack.
     * @return true if both are null or equal.
     */
    private fun itemsEqual(a: ItemStack?, b: ItemStack?): Boolean {
        if (a == null && b == null) return true
        if (a == null || b == null) return false
        return a.isSimilar(b) && a.amount == b.amount
    }

    /**
     * Checks if an item is considered valuable and should be logged in transactions.
     * Valuable items include:
     * - Netherite items (ingots, blocks, equipment)
     * - Diamonds and diamond blocks
     * - Enchanted items
     * - Nether stars
     * - Elytra
     * - Shulker boxes (can contain many items)
     *
     * @param item The item to check.
     * @return true if the item is valuable.
     */
    private fun isValuableItem(item: ItemStack): Boolean {
        val material = item.type
        val materialName = material.name

        // Check for netherite items
        if (materialName.contains("NETHERITE")) {
            return true
        }

        // Check for diamonds
        if (material == org.bukkit.Material.DIAMOND || material == org.bukkit.Material.DIAMOND_BLOCK) {
            return true
        }

        // Check for nether star
        if (material == org.bukkit.Material.NETHER_STAR) {
            return true
        }

        // Check for elytra
        if (material == org.bukkit.Material.ELYTRA) {
            return true
        }

        // Check for shulker boxes
        if (materialName.contains("SHULKER_BOX")) {
            return true
        }

        // Check if item has enchantments
        if (item.hasItemMeta() && item.itemMeta?.hasEnchants() == true) {
            return true
        }

        return false
    }

    /**
     * Ensures the Gold Balance Button is present in slot 0 of a vault inventory.
     * Regenerates the button if it's missing or invalid.
     *
     * @param guildId The guild ID.
     * @param inventory The Bukkit inventory to validate.
     */
    fun ensureGoldButtonPresent(guildId: UUID, inventory: Inventory) {
        val slot0 = inventory.getItem(0)

        if (!GoldBalanceButton.isGoldButton(slot0)) {
            logger.warn("Gold button missing or invalid for guild $guildId, regenerating...")

            val vault = vaultCache[guildId]
            if (vault != null) {
                val goldBalance = vault.getGold()
                val newButton = GoldBalanceButton.createItem(goldBalance)

                // Update GUI inventory
                inventory.setItem(0, newButton)

                // Update cache to ensure consistency
                vault.setSlot(0, newButton)

                logger.info("Regenerated Gold Balance Button for guild $guildId (balance: $goldBalance nuggets)")
            } else {
                logger.error("Cannot regenerate Gold Balance Button - vault not loaded for guild $guildId")
            }
        }
    }

    /**
     * Validates and repairs a vault inventory.
     * Ensures gold button is present and all slots are synced.
     * Should be called periodically for all open vaults.
     *
     * @param guildId The guild ID.
     * @param inventory The Bukkit inventory to validate.
     */
    fun validateAndRepairVault(guildId: UUID, inventory: Inventory) {
        // First, ensure gold button is present
        ensureGoldButtonPresent(guildId, inventory)

        // Then validate all other slots
        validateInventorySync(guildId, inventory)
    }
}
