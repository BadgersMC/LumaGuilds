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
 */
class VaultInventoryHolder(
    val guildId: UUID,
    val guildName: String,
    private val vaultInventoryManager: VaultInventoryManager,
    private val capacity: Int = 54
) : InventoryHolder {

    private val inventory: Inventory = Bukkit.createInventory(
        this,
        capacity,
        net.kyori.adventure.text.Component.text("$guildName Vault")
    )

    init {
        // Load inventory from cache on creation
        loadInventoryFromCache()
    }

    /**
     * Gets the Bukkit inventory instance.
     * Required by InventoryHolder interface.
     */
    override fun getInventory(): Inventory {
        return inventory
    }

    /**
     * Loads the vault inventory from the cache and populates the Bukkit inventory.
     * Slot 0 is always populated with the Gold Balance Button.
     */
    fun loadInventoryFromCache() {
        // Clear inventory first
        inventory.clear()

        // Load gold balance and create button for slot 0
        val goldBalance = vaultInventoryManager.getGoldBalance(guildId)
        val goldButton = GoldBalanceButton.createItem(goldBalance)
        inventory.setItem(0, goldButton)

        // Load all regular slots (1-53)
        val slots = vaultInventoryManager.getAllSlots(guildId)
        slots.forEach { (slot, item) ->
            if (slot > 0 && slot < capacity) {
                inventory.setItem(slot, item)
            }
        }
    }

    /**
     * Syncs the current Bukkit inventory state back to the cache.
     * Does not write to database - that happens via the write buffer.
     * Slot 0 is skipped as it's always the Gold Balance Button.
     */
    fun syncToCache() {
        for (slot in 1 until capacity) {
            val item = inventory.getItem(slot)
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
        inventory.setItem(0, goldButton)
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

        inventory.setItem(slot, item)
        vaultInventoryManager.updateSlotWithBroadcast(guildId, slot, item)
    }

    /**
     * Gets the vault capacity.
     */
    fun getCapacity(): Int {
        return capacity
    }
}
