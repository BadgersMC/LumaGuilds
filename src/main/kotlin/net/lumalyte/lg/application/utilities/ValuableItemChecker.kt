package net.lumalyte.lg.application.utilities

import net.lumalyte.lg.config.VaultConfig
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * Utility for determining if an item is valuable enough to warrant immediate database flush
 * and transaction logging. This prevents data loss during crashes for high-value items.
 */
object ValuableItemChecker {

    /**
     * Checks if an item is valuable based on configuration.
     *
     * @param item The item to check
     * @param config The vault configuration containing valuable item definitions
     * @return true if the item is valuable and should trigger immediate database flush
     */
    fun isValuableItem(item: ItemStack, config: VaultConfig): Boolean {
        val material = item.type
        val materialName = material.name

        // Check if material is in the valuable items list
        if (config.valuableItems.any { it.equals(materialName, ignoreCase = true) }) {
            return true
        }

        // Check custom model data items
        if (item.hasItemMeta() && item.itemMeta?.hasCustomModelData() == true) {
            val customModelData = item.itemMeta?.customModelData
            val itemSignature = "$materialName:$customModelData"

            if (config.valuableCustomModelDataItems.any { it.equals(itemSignature, ignoreCase = true) }) {
                return true
            }
        }

        // Check if item has enchantments (if enabled in config)
        if (config.valuableItemsCheckEnchantments) {
            if (item.hasItemMeta() && item.itemMeta?.hasEnchants() == true) {
                return true
            }
        }

        return false
    }

    /**
     * Bulk check for valuable items in a collection.
     * Returns true if ANY item in the collection is valuable.
     */
    fun containsValuableItems(items: Collection<ItemStack>, config: VaultConfig): Boolean {
        return items.any { isValuableItem(it, config) }
    }

    /**
     * Counts the number of valuable items in a collection.
     */
    fun countValuableItems(items: Collection<ItemStack>, config: VaultConfig): Int {
        return items.count { isValuableItem(it, config) }
    }
}
