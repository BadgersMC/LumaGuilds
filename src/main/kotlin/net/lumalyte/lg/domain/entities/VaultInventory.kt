package net.lumalyte.lg.domain.entities

import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Represents the in-memory state of a guild's vault.
 * Uses thread-safe data structures for concurrent access from multiple players.
 */
class VaultInventory(
    val guildId: UUID,
    /**
     * Map of slot index to ItemStack.
     * Slot 0 is reserved for the Gold Balance Button.
     * ConcurrentHashMap ensures thread-safe concurrent access.
     */
    val slots: ConcurrentHashMap<Int, ItemStack?> = ConcurrentHashMap(),

    /**
     * Gold balance in nugget equivalents (1 block = 81 nuggets, 1 ingot = 9 nuggets).
     * AtomicLong ensures thread-safe atomic updates for deposits/withdrawals.
     */
    val goldBalance: AtomicLong = AtomicLong(0),

    /**
     * Timestamp of when this vault was last loaded from the database.
     */
    var lastLoadedTimestamp: Long = System.currentTimeMillis(),

    /**
     * Timestamp of when this vault was last modified.
     */
    var lastModifiedTimestamp: Long = System.currentTimeMillis(),

    /**
     * Dirty flag indicating pending database writes.
     * Used for retry logic when database writes fail.
     */
    @Volatile
    var isDirty: Boolean = false
) {
    /**
     * Updates a slot in the vault.
     * Returns the previous item in that slot, or null if empty.
     */
    fun setSlot(slot: Int, item: ItemStack?): ItemStack? {
        lastModifiedTimestamp = System.currentTimeMillis()
        return if (item == null) {
            slots.remove(slot)
        } else {
            slots.put(slot, item)
        }
    }

    /**
     * Gets an item from a slot.
     * Returns null if the slot is empty.
     */
    fun getSlot(slot: Int): ItemStack? {
        return slots[slot]
    }

    /**
     * Adds to the gold balance.
     * Returns the new balance.
     */
    fun addGold(amount: Long): Long {
        lastModifiedTimestamp = System.currentTimeMillis()
        return goldBalance.addAndGet(amount)
    }

    /**
     * Subtracts from the gold balance if sufficient funds exist.
     * Returns the new balance, or -1 if insufficient funds.
     */
    fun subtractGold(amount: Long): Long {
        lastModifiedTimestamp = System.currentTimeMillis()

        // Atomic compare-and-swap loop to ensure we never go negative
        var current: Long
        var newValue: Long
        do {
            current = goldBalance.get()
            if (current < amount) {
                return -1L // Insufficient balance
            }
            newValue = current - amount
        } while (!goldBalance.compareAndSet(current, newValue))

        return newValue
    }

    /**
     * Gets the current gold balance.
     */
    fun getGold(): Long {
        return goldBalance.get()
    }

    /**
     * Sets the gold balance to a specific value.
     * Used when loading from database.
     */
    fun setGold(amount: Long) {
        goldBalance.set(amount)
    }

    /**
     * Marks this vault as dirty, indicating it has pending database writes.
     * Used when a database write fails and needs to be retried.
     */
    fun markDirty() {
        isDirty = true
    }

    /**
     * Clears the dirty flag after successful database write.
     */
    fun clearDirty() {
        isDirty = false
    }
}
