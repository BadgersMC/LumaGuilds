package net.lumalyte.lg.infrastructure.adapters.bukkit

import net.lumalyte.lg.domain.values.InventoryView
import net.lumalyte.lg.domain.values.Item
import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory

/**
 * Adapter that wraps a Bukkit Inventory to implement the domain InventoryView interface
 */
class BukkitInventoryAdapter(
    private val bukkitInventory: Inventory
) : InventoryView {

    override val size: Int
        get() = bukkitInventory.size

    override val title: String
        get() = bukkitInventory.type.name // Bukkit doesn't always expose title easily

    override fun getItem(slot: Int): Item? {
        return bukkitInventory.getItem(slot).toItem().takeIf { it.isNotEmpty() }
    }

    override fun setItem(slot: Int, item: Item?) {
        bukkitInventory.setItem(slot, item?.toItemStack())
    }

    override fun getContents(): Map<Int, Item> {
        return bukkitInventory.contents.toItemMap()
    }

    override fun setContents(items: Map<Int, Item>) {
        bukkitInventory.clear()
        items.forEach { (slot, item) ->
            setItem(slot, item)
        }
    }

    override fun clear() {
        bukkitInventory.clear()
    }

    override fun contains(item: Item): Boolean {
        val bukkitItem = item.toItemStack()
        return bukkitInventory.contains(bukkitItem.type, item.amount)
    }

    override fun hasSpace(item: Item): Boolean {
        // Check if there's at least one empty slot or stackable slot
        return bukkitInventory.firstEmpty() != -1 ||
               bukkitInventory.contents.any { existingItem ->
                   existingItem != null &&
                   existingItem.type.name == item.type &&
                   existingItem.amount + item.amount <= existingItem.maxStackSize
               }
    }

    override fun addItem(item: Item): Item? {
        val bukkitItem = item.toItemStack()
        val remaining = bukkitInventory.addItem(bukkitItem)

        return if (remaining.isEmpty()) {
            null
        } else {
            remaining.values.first().toItem()
        }
    }

    override fun removeItem(item: Item): Boolean {
        val bukkitItem = item.toItemStack()
        val remaining = bukkitInventory.removeItem(bukkitItem)
        return remaining.isEmpty()
    }

    override fun countItemType(type: String): Int {
        return bukkitInventory.contents
            .filterNotNull()
            .filter { it.type.name == type }
            .sumOf { it.amount }
    }

    /**
     * Gets the underlying Bukkit inventory
     */
    fun toBukkitInventory(): Inventory = bukkitInventory
}

/**
 * Factory for creating Bukkit inventories from domain InventoryView specifications
 */
object BukkitInventoryFactory {

    /**
     * Creates a Bukkit chest inventory with the specified size and title
     */
    fun createChestInventory(size: Int, title: String): BukkitInventoryAdapter {
        require(size % 9 == 0 && size in 9..54) {
            "Chest inventory size must be a multiple of 9 between 9 and 54"
        }

        val inventory = Bukkit.createInventory(null, size, title)
        return BukkitInventoryAdapter(inventory)
    }

    /**
     * Creates a Bukkit inventory from a domain InventoryView
     */
    fun fromInventoryView(view: InventoryView): BukkitInventoryAdapter {
        val inventory = Bukkit.createInventory(null, view.size, view.title)
        view.getContents().forEach { (slot, item) ->
            inventory.setItem(slot, item.toItemStack())
        }
        return BukkitInventoryAdapter(inventory)
    }

    /**
     * Wraps an existing Bukkit inventory
     */
    fun wrap(inventory: Inventory): BukkitInventoryAdapter {
        return BukkitInventoryAdapter(inventory)
    }
}

// Extension functions for easier usage
fun Inventory.toInventoryView(): InventoryView = BukkitInventoryAdapter(this)
fun InventoryView.toBukkitInventory(): Inventory {
    return when (this) {
        is BukkitInventoryAdapter -> this.toBukkitInventory()
        else -> BukkitInventoryFactory.fromInventoryView(this).toBukkitInventory()
    }
}
