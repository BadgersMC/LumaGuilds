package net.lumalyte.lg.interaction.inventory

import net.lumalyte.lg.application.services.VaultInventoryManager
import net.lumalyte.lg.application.utilities.GoldBalanceButton
import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID

/**
 * Custom InventoryHolder for guild vaults.
 * Manages the 54-slot chest inventory and syncs with VaultInventoryManager.
 * Slot 0 is reserved for the Gold Balance Button.
 *
 * Supports two modes:
 * 1. Shared inventory mode: A shared Inventory is set via setSharedInventory() - all viewers share it
 * 2. Legacy mode: Creates its own Inventory (deprecated, for backward compatibility)
 */
class VaultInventoryHolder(
    val guildId: UUID,
    val guildName: String,
    private val vaultInventoryManager: VaultInventoryManager,
    private val capacity: Int = 54
) : InventoryHolder {

    /**
     * Shared inventory reference. When set, this inventory is used instead of creating a new one.
     * All players viewing the same guild vault share this single Inventory instance.
     */
    private var sharedInventory: Inventory? = null

    /**
     * Legacy inventory - only created if needed (when not using shared inventory).
     * Lazily initialized to avoid creating unused inventories.
     */
    private val legacyInventory: Inventory by lazy {
        val inv = Bukkit.createInventory(
            this,
            capacity,
            net.kyori.adventure.text.Component.text("$guildName Vault")
        )
        // Load inventory from cache on creation (legacy mode only)
        loadInventoryFromCacheInto(inv)
        inv
    }

    /**
     * Sets the shared inventory instance.
     * When set, getInventory() will return this shared instance instead of creating a new one.
     * This enables multiple players to view the same Inventory object.
     *
     * @param inventory The shared Inventory to use.
     */
    fun setSharedInventory(inventory: Inventory) {
        this.sharedInventory = inventory
    }

    /**
     * Gets the Bukkit inventory instance.
     * Returns the shared inventory if set, otherwise falls back to legacy behavior.
     * Required by InventoryHolder interface.
     */
    override fun getInventory(): Inventory {
        return sharedInventory ?: legacyInventory
    }

    /**
     * Loads the vault inventory from the cache and populates the given Bukkit inventory.
     * Slot 0 is always populated with the Gold Balance Button.
     *
     * @param inv The inventory to populate.
     */
    private fun loadInventoryFromCacheInto(inv: Inventory) {
        // Clear inventory first
        inv.clear()

        // Load gold balance and create button for slot 0
        val goldBalance = vaultInventoryManager.getGoldBalance(guildId)
        val goldButton = GoldBalanceButton.createItem(goldBalance)
        inv.setItem(0, goldButton)

        // Load all regular slots (1-53)
        val slots = vaultInventoryManager.getAllSlots(guildId)
        slots.forEach { (slot, item) ->
            if (slot > 0 && slot < capacity) {
                inv.setItem(slot, item)
            }
        }
    }

    /**
     * Loads the vault inventory from the cache and populates the current Bukkit inventory.
     * Slot 0 is always populated with the Gold Balance Button.
     * @deprecated Use shared inventory mode instead.
     */
    @Deprecated("Use shared inventory mode via setSharedInventory()")
    fun loadInventoryFromCache() {
        loadInventoryFromCacheInto(getInventory())
    }

    /**
     * Syncs the current Bukkit inventory state back to the cache.
     * Does not write to database - that happens via the write buffer.
     * Slot 0 is skipped as it's always the Gold Balance Button.
     */
    fun syncToCache() {
        val inv = getInventory()
        for (slot in 1 until capacity) {
            val item = inv.getItem(slot)
            vaultInventoryManager.updateSlot(guildId, slot, item)
        }
    }

    /**
     * Updates the Gold Balance Button in slot 0 with the current balance.
     * Called when the gold balance changes.
     */
    fun updateGoldButton() {
        val goldBalance = vaultInventoryManager.getGoldBalance(guildId)
        val goldButton = GoldBalanceButton.createItem(goldBalance)
        getInventory().setItem(0, goldButton)
    }

    /**
     * Updates a specific slot in the inventory and syncs to cache.
     *
     * @param slot The slot index.
     * @param item The new item (null to clear).
     */
    fun updateSlot(slot: Int, item: org.bukkit.inventory.ItemStack?) {
        if (slot == 0) {
            // Slot 0 is reserved for gold button, cannot be updated directly
            return
        }

        if (slot >= capacity) {
            return
        }

        getInventory().setItem(slot, item)
        vaultInventoryManager.updateSlotWithBroadcast(guildId, slot, item)
    }

    /**
     * Gets the vault capacity.
     */
    fun getCapacity(): Int {
        return capacity
    }
}
