package net.lumalyte.lg.infrastructure.hytale.adapters

import com.hypixel.hytale.server.core.inventory.container.ItemContainer
import com.hypixel.hytale.server.core.inventory.ItemStack as HytaleItemStack
import com.hypixel.hytale.server.core.asset.type.item.config.Item as HytaleItem
import net.lumalyte.lg.domain.values.InventoryView
import net.lumalyte.lg.domain.values.Item
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("HytaleInventoryAdapter")

/**
 * Adapts Hytale's ItemContainer to domain's InventoryView interface
 */
class HytaleInventoryAdapter(
    private val container: ItemContainer,
    override val title: String = "Inventory"
) : InventoryView {

    override val size: Int
        get() = container.capacity.toInt()

    override fun getItem(slot: Int): Item? {
        require(slot in 0 until size) { "Slot $slot out of bounds (size: $size)" }

        val stack = container.getItemStack(slot.toShort())
        return stack?.takeIf { !it.isEmpty }?.toItem()
    }

    override fun setItem(slot: Int, item: Item?) {
        require(slot in 0 until size) { "Slot $slot out of bounds (size: $size)" }

        val hytaleStack = item?.toHytaleItemStack()
        container.setItemStackForSlot(slot.toShort(), hytaleStack)
    }

    override fun getContents(): Map<Int, Item> {
        val contents = mutableMapOf<Int, Item>()

        for (slot in 0 until size) {
            getItem(slot)?.let { item ->
                contents[slot] = item
            }
        }

        return contents
    }

    override fun setContents(items: Map<Int, Item>) {
        clear()
        items.forEach { (slot, item) ->
            setItem(slot, item)
        }
    }

    override fun clear() {
        container.clear()
    }

    override fun contains(item: Item): Boolean {
        for (slot in 0 until size) {
            val existing = getItem(slot)
            if (existing != null && existing.type == item.type && existing.amount >= item.amount) {
                return true
            }
        }
        return false
    }

    override fun hasSpace(item: Item): Boolean {
        // Check for empty slots
        for (slot in 0 until size) {
            if (getItem(slot) == null) return true
        }

        // Check for stackable items
        for (slot in 0 until size) {
            val existing = getItem(slot)
            if (existing != null && existing.type == item.type) {
                val hytaleItem = HytaleItem.getAssetMap().getAsset(existing.type)
                val maxStack = hytaleItem?.maxStack ?: 64

                if (existing.amount + item.amount <= maxStack) {
                    return true
                }
            }
        }

        return false
    }

    override fun addItem(item: Item): Item? {
        val hytaleStack = item.toHytaleItemStack() ?: run {
            log.warn("Cannot add item: ${item.type} not found in Hytale registry")
            return item
        }

        val result = container.addItemStack(hytaleStack)

        // Check if any items remain
        val remainder = result.remainder
        return remainder?.takeIf { !it.isEmpty }?.toItem()
    }

    override fun removeItem(item: Item): Boolean {
        val hytaleStack = item.toHytaleItemStack() ?: return false

        val result = container.removeItemStack(hytaleStack)
        return result.succeeded()
    }

    override fun countItemType(type: String): Int {
        var count = 0
        for (slot in 0 until size) {
            val item = getItem(slot)
            if (item != null && item.type == type) {
                count += item.amount
            }
        }
        return count
    }
}

/**
 * Extension function to convert ItemContainer to InventoryView
 */
fun ItemContainer.toInventoryView(title: String = "Inventory"): InventoryView {
    return HytaleInventoryAdapter(this, title)
}
