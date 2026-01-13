package net.lumalyte.lg.infrastructure.adapters.bukkit

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.domain.values.Item
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType

/**
 * Adapter for converting between Bukkit ItemStack and domain Item
 */
object BukkitItemAdapter {

    /**
     * Converts a Bukkit ItemStack to a domain Item
     */
    fun ItemStack?.toItem(): Item {
        if (this == null || type == Material.AIR || amount <= 0) {
            return Item.empty()
        }

        val meta = itemMeta
        val displayName = meta?.displayName()?.let {
            PlainTextComponentSerializer.plainText().serialize(it)
        }

        val lore = meta?.lore()?.map {
            PlainTextComponentSerializer.plainText().serialize(it)
        } ?: emptyList()

        val enchantments = enchantments.mapKeys { (enchant, _) ->
            enchant.key.key
        }

        val customModelData = meta?.customModelData

        // Extract persistent data container as NBT
        val nbtData = mutableMapOf<String, Any>()
        meta?.persistentDataContainer?.keys?.forEach { key ->
            val value = meta.persistentDataContainer.get(key, PersistentDataType.STRING)
            if (value != null) {
                nbtData[key.toString()] = value
            }
        }

        return Item(
            type = type.name,
            amount = amount,
            displayName = displayName,
            lore = lore,
            enchantments = enchantments,
            customModelData = customModelData,
            nbtData = nbtData
        )
    }

    /**
     * Converts a domain Item to a Bukkit ItemStack
     */
    fun Item.toItemStack(): ItemStack {
        if (isEmpty()) {
            return ItemStack(Material.AIR)
        }

        val material = Material.getMaterial(type)
            ?: throw IllegalArgumentException("Unknown material type: $type")

        val itemStack = ItemStack(material, amount)

        itemStack.editMeta { meta ->
            // Set display name
            displayName?.let {
                meta.displayName(Component.text(it))
            }

            // Set lore
            if (lore.isNotEmpty()) {
                meta.lore(lore.map { Component.text(it) })
            }

            // Set custom model data
            customModelData?.let {
                meta.setCustomModelData(it)
            }

            // Note: Enchantments and NBT data would need more sophisticated handling
            // This is a simplified version for the migration
        }

        // Add enchantments
        enchantments.forEach { (enchantKey, level) ->
            val enchantment = Enchantment.getByKey(
                org.bukkit.NamespacedKey.minecraft(enchantKey)
            )
            if (enchantment != null) {
                itemStack.addUnsafeEnchantment(enchantment, level)
            }
        }

        return itemStack
    }

    /**
     * Converts a list of Bukkit ItemStacks to domain Items
     */
    fun List<ItemStack?>.toItems(): List<Item> {
        return map { it.toItem() }
    }

    /**
     * Converts a list of domain Items to Bukkit ItemStacks
     */
    fun List<Item>.toItemStacks(): List<ItemStack> {
        return map { it.toItemStack() }
    }

    /**
     * Converts an array of Bukkit ItemStacks to a map of slot -> Item
     */
    fun Array<ItemStack?>.toItemMap(): Map<Int, Item> {
        return mapIndexedNotNull { index, itemStack ->
            itemStack?.toItem()?.let { item ->
                if (item.isNotEmpty()) index to item else null
            }
        }.toMap()
    }

    /**
     * Converts a map of slot -> Item to an array of Bukkit ItemStacks
     */
    fun Map<Int, Item>.toItemStackArray(size: Int): Array<ItemStack?> {
        val array = arrayOfNulls<ItemStack>(size)
        forEach { (slot, item) ->
            if (slot < size) {
                array[slot] = item.toItemStack()
            }
        }
        return array
    }
}

// Extension functions for easier usage
fun ItemStack?.toItem(): Item = BukkitItemAdapter.run { toItem() }
fun Item.toItemStack(): ItemStack = BukkitItemAdapter.run { toItemStack() }
fun List<ItemStack?>.toItems(): List<Item> = BukkitItemAdapter.run { toItems() }
fun List<Item>.toItemStacks(): List<ItemStack> = BukkitItemAdapter.run { toItemStacks() }
fun Array<ItemStack?>.toItemMap(): Map<Int, Item> = BukkitItemAdapter.run { toItemMap() }
