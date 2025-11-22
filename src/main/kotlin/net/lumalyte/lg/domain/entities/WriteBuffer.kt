package net.lumalyte.lg.domain.entities

import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents pending database writes for a vault.
 * Batches multiple slot changes together to reduce database I/O.
 */
class WriteBuffer(
    val guildId: UUID,

    /**
     * Map of slot index to pending ItemStack writes.
     * ConcurrentHashMap ensures thread-safe access.
     * Note: Does NOT support null values - use pendingDeletions for cleared slots.
     */
    val pendingSlots: ConcurrentHashMap<Int, ItemStack> = ConcurrentHashMap(),

    /**
     * Set of slot indices that need to be cleared (deleted) in the database.
     */
    val pendingDeletions: MutableSet<Int> = mutableSetOf(),

    /**
     * Pending gold balance write, or null if no gold write is pending.
     */
    var pendingGoldBalance: Long? = null,

    /**
     * Timestamp when the first change was buffered.
     * Used to trigger flush after a time threshold.
     */
    var firstChangeTimestamp: Long = System.currentTimeMillis(),

    /**
     * Timestamp of the most recent change.
     */
    var lastChangeTimestamp: Long = System.currentTimeMillis()
) {
    /**
     * Adds a slot change to the buffer.
     * Note: ConcurrentHashMap doesn't allow null values, so we remove the key instead.
     */
    fun bufferSlotChange(slot: Int, item: ItemStack?) {
        if (item == null) {
            // Store a sentinel value to indicate deletion, or track separately
            pendingSlots.remove(slot)
            // But we still need to track that this slot needs to be cleared in DB
            // So let's use a separate set for tracking deletions
            pendingDeletions.add(slot)
        } else {
            pendingSlots[slot] = item
            pendingDeletions.remove(slot) // Remove from deletions if re-added
        }
        lastChangeTimestamp = System.currentTimeMillis()
    }

    /**
     * Adds a gold balance change to the buffer.
     */
    fun bufferGoldChange(newBalance: Long) {
        pendingGoldBalance = newBalance
        lastChangeTimestamp = System.currentTimeMillis()
    }

    /**
     * Checks if the buffer has any pending changes.
     */
    fun hasPendingChanges(): Boolean {
        return pendingSlots.isNotEmpty() || pendingDeletions.isNotEmpty() || pendingGoldBalance != null
    }

    /**
     * Checks if the buffer should be flushed based on size or time thresholds.
     *
     * @param maxSlots Maximum number of pending slots before forcing flush (default 5).
     * @param maxAgeMs Maximum age of oldest change before forcing flush (default 1000ms = 1 second).
     * @return true if the buffer should be flushed.
     */
    fun shouldFlush(maxSlots: Int = 5, maxAgeMs: Long = 1000): Boolean {
        if (!hasPendingChanges()) return false

        val slotCountExceeded = pendingSlots.size >= maxSlots
        val timeThresholdExceeded = (System.currentTimeMillis() - firstChangeTimestamp) >= maxAgeMs

        return slotCountExceeded || timeThresholdExceeded
    }

    /**
     * Clears all pending changes.
     * Called after successfully flushing to the database.
     */
    fun clear() {
        pendingSlots.clear()
        pendingDeletions.clear()
        pendingGoldBalance = null
        firstChangeTimestamp = System.currentTimeMillis()
        lastChangeTimestamp = System.currentTimeMillis()
    }

    /**
     * Gets the age of the oldest pending change in milliseconds.
     */
    fun getAge(): Long {
        return System.currentTimeMillis() - firstChangeTimestamp
    }
}
