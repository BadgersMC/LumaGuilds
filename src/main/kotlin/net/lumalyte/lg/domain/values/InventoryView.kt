package net.lumalyte.lg.domain.values

/**
 * Platform-agnostic representation of an inventory.
 * This abstraction allows the domain layer to work with inventories
 * without depending on any specific game engine (Bukkit, Hytale, etc.)
 */
interface InventoryView {
    /**
     * The size of this inventory (number of slots)
     */
    val size: Int

    /**
     * The title/name of this inventory
     */
    val title: String

    /**
     * Gets the item at the specified slot
     * @param slot The slot index (0-based)
     * @return The item at that slot, or null if empty
     */
    fun getItem(slot: Int): Item?

    /**
     * Sets an item in the specified slot
     * @param slot The slot index (0-based)
     * @param item The item to set, or null to clear the slot
     */
    fun setItem(slot: Int, item: Item?)

    /**
     * Gets all items in this inventory
     * @return A map of slot index to item (only non-null items)
     */
    fun getContents(): Map<Int, Item>

    /**
     * Sets all items in this inventory
     * @param items Map of slot index to item
     */
    fun setContents(items: Map<Int, Item>)

    /**
     * Clears all items from this inventory
     */
    fun clear()

    /**
     * Checks if this inventory contains the specified item
     * @param item The item to search for
     * @return True if the inventory contains at least one of this item
     */
    fun contains(item: Item): Boolean

    /**
     * Checks if this inventory has space for the specified item
     * @param item The item to check
     * @return True if there's space to add this item
     */
    fun hasSpace(item: Item): Boolean

    /**
     * Adds an item to the inventory
     * @param item The item to add
     * @return The remaining item stack if it didn't all fit, null if it all fit
     */
    fun addItem(item: Item): Item?

    /**
     * Removes an item from the inventory
     * @param item The item to remove
     * @return True if the item was found and removed
     */
    fun removeItem(item: Item): Boolean

    /**
     * Counts how many of a specific item type are in the inventory
     * @param type The item type to count
     * @return The total amount of this item type
     */
    fun countItemType(type: String): Int
}

/**
 * Simple in-memory implementation of InventoryView for testing and domain logic
 */
class SimpleInventoryView(
    override val size: Int,
    override val title: String = "Inventory"
) : InventoryView {
    private val contents: MutableMap<Int, Item> = mutableMapOf()

    init {
        require(size > 0) { "Inventory size must be positive" }
    }

    override fun getItem(slot: Int): Item? {
        require(slot in 0 until size) { "Slot $slot is out of bounds for size $size" }
        return contents[slot]
    }

    override fun setItem(slot: Int, item: Item?) {
        require(slot in 0 until size) { "Slot $slot is out of bounds for size $size" }
        if (item == null || item.isEmpty()) {
            contents.remove(slot)
        } else {
            contents[slot] = item
        }
    }

    override fun getContents(): Map<Int, Item> = contents.toMap()

    override fun setContents(items: Map<Int, Item>) {
        clear()
        items.forEach { (slot, item) ->
            setItem(slot, item)
        }
    }

    override fun clear() {
        contents.clear()
    }

    override fun contains(item: Item): Boolean {
        return contents.values.any { it.type == item.type && it.amount >= item.amount }
    }

    override fun hasSpace(item: Item): Boolean {
        // Check for empty slots
        if (contents.size < size) return true

        // Check for stackable items
        return contents.values.any { existingItem ->
            existingItem.type == item.type &&
            existingItem.amount + item.amount <= 64 // Assuming max stack size of 64
        }
    }

    override fun addItem(item: Item): Item? {
        var remaining = item

        // Try to stack with existing items
        for (slot in 0 until size) {
            val existingItem = contents[slot]
            if (existingItem != null && existingItem.type == item.type) {
                val maxStack = 64
                val canAdd = maxStack - existingItem.amount
                if (canAdd > 0) {
                    val amountToAdd = minOf(canAdd, remaining.amount)
                    contents[slot] = existingItem.withAmount(existingItem.amount + amountToAdd)
                    remaining = remaining.withAmount(remaining.amount - amountToAdd)

                    if (remaining.amount <= 0) return null
                }
            }
        }

        // Find empty slots
        for (slot in 0 until size) {
            if (contents[slot] == null) {
                contents[slot] = remaining
                return null
            }
        }

        return remaining
    }

    override fun removeItem(item: Item): Boolean {
        var toRemove = item.amount

        for ((slot, existingItem) in contents.toList()) {
            if (existingItem.type == item.type) {
                if (existingItem.amount >= toRemove) {
                    val newAmount = existingItem.amount - toRemove
                    if (newAmount <= 0) {
                        contents.remove(slot)
                    } else {
                        contents[slot] = existingItem.withAmount(newAmount)
                    }
                    return true
                } else {
                    toRemove -= existingItem.amount
                    contents.remove(slot)
                }
            }
        }

        return toRemove <= 0
    }

    override fun countItemType(type: String): Int {
        return contents.values
            .filter { it.type == type }
            .sumOf { it.amount }
    }
}
