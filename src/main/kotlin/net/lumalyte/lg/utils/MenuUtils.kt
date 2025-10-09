package net.lumalyte.lg.utils

import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * Utility class for creating GUI menu items with consistent styling.
 */
object MenuUtils {

    /**
     * Creates a menu item with specified material, name, and lore.
     *
     * @param material The material for the item
     * @param name The display name of the item
     * @param lore Optional list of lore lines
     * @return The created ItemStack
     */
    fun createMenuItem(material: Material, name: String, lore: List<String> = emptyList()): ItemStack {
        return ItemStack(material).apply {
            name(name)
            if (lore.isNotEmpty()) {
                lore(lore)
            }
        }
    }

    /**
     * Creates a menu item with specified material, name, and lore as varargs.
     *
     * @param material The material for the item
     * @param name The display name of the item
     * @param lore Variable number of lore lines
     * @return The created ItemStack
     */
    fun createMenuItem(material: Material, name: String, vararg lore: String): ItemStack {
        return createMenuItem(material, name, lore.toList())
    }
}
