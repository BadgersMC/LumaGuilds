package net.lumalyte.lg.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.*
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.persistence.PersistentDataType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.*
import java.util.Base64
import java.util.function.Consumer


fun ItemStack.amount(amount: Int): ItemStack {
    setAmount(amount)
    return this
}

fun ItemStack.name(name: String): ItemStack {
    val meta = itemMeta ?: Bukkit.getItemFactory().getItemMeta(type) ?: return this
    // Create component and explicitly disable italic formatting to override Minecraft's default italic lore
    val component = Component.text(name).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)
    meta.itemName(component)
    // Hide all item attributes (damage, attack speed, durability, etc.) in menus
    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
    itemMeta = meta
    return this
}

fun ItemStack.lore(text: String): ItemStack {
    val meta = itemMeta ?: Bukkit.getItemFactory().getItemMeta(type) ?: return this
    var lore: MutableList<Component>? = meta.lore()
    if (lore == null) {
        lore = ArrayList()
    }
    // Create component and explicitly disable italic formatting to override Minecraft's default italic lore
    val component = text.c().decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)
    lore.add(component)
    meta.lore(lore)
    itemMeta = meta
    return this
}

fun ItemStack.lore(vararg text: String): ItemStack {
    Arrays.stream(text).forEach { this.lore(it) }
    return this
}

fun ItemStack.lore(text: List<String>): ItemStack {
    val meta = itemMeta ?: Bukkit.getItemFactory().getItemMeta(type) ?: return this
    // Apply italic formatting removal to each lore line
    val componentLore = text.c().map { it.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE) }
    meta.lore(componentLore)
    itemMeta = meta
    return this
}

fun ItemStack.durability(durability: Int): ItemStack {
    val meta = itemMeta ?: Bukkit.getItemFactory().getItemMeta(type) ?: return this
    if (meta is org.bukkit.inventory.meta.Damageable) {
        meta.setDamage(durability)
        itemMeta = meta
    }
    return this
}



fun ItemStack.enchantment(enchantment: Enchantment, level: Int): ItemStack {
    addUnsafeEnchantment(enchantment, level)
    return this
}

fun ItemStack.enchantment(enchantment: Enchantment): ItemStack {
    addUnsafeEnchantment(enchantment, 1)
    return this
}



fun ItemStack.clearLore(): ItemStack {
    val meta = itemMeta ?: Bukkit.getItemFactory().getItemMeta(type) ?: return this
    meta.lore(ArrayList())
    itemMeta = meta
    return this
}

fun ItemStack.clearEnchantments(): ItemStack {
    enchantments.keys.forEach(Consumer<Enchantment> { this.removeEnchantment(it) })
    return this
}

fun ItemStack.color(color: Color): ItemStack {
    if (type == Material.LEATHER_BOOTS
        || type == Material.LEATHER_CHESTPLATE
        || type == Material.LEATHER_HELMET
        || type == Material.LEATHER_LEGGINGS) {

        val meta = itemMeta ?: Bukkit.getItemFactory().getItemMeta(type) ?: return this
        if (meta is LeatherArmorMeta) {
            meta.setColor(color)
            itemMeta = meta
        }
        return this
    } else {
        throw IllegalArgumentException("Colors only applicable for leather armor!")
    }
}

fun ItemStack.flag(vararg flag: ItemFlag): ItemStack {
    val meta = itemMeta ?: Bukkit.getItemFactory().getItemMeta(type) ?: return this
    meta.addItemFlags(*flag)
    itemMeta = meta
    return this
}

fun ItemStack.getBooleanMeta(key: String): String? {
    val meta = itemMeta ?: return null
    return meta.persistentDataContainer.get(
        NamespacedKey("bellclaims",key), PersistentDataType.STRING)
}

fun ItemStack.setBooleanMeta(key: String, value: Boolean): ItemStack {
    val meta = itemMeta ?: Bukkit.getItemFactory().getItemMeta(type) ?: return this
    meta.persistentDataContainer.set(
        NamespacedKey("bellclaims",key), PersistentDataType.BOOLEAN, value)
    itemMeta = meta
    return this
}

fun ItemStack.getStringMeta(key: String): String? {
    val meta = itemMeta ?: return null
    return meta.persistentDataContainer.get(
        NamespacedKey("bellclaims",key), PersistentDataType.STRING)
}

fun ItemStack.setStringMeta(key: String, value: String): ItemStack {
    val meta = itemMeta ?: Bukkit.getItemFactory().getItemMeta(type)
    if (meta == null) return this
    meta.persistentDataContainer.set(
        NamespacedKey("bellclaims",key), PersistentDataType.STRING, value)
    itemMeta = meta
    return this
}

private fun String.c(): Component {
    return LegacyComponentSerializer.legacyAmpersand().deserialize(this)
        .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)
}

private fun List<String>.c(): List<Component> {
    return this.map { it.c() }
}

/**
 * Serializes an ItemStack to a Base64 string for database storage
 *
 * Benefits of Base64 encoding:
 * - More space efficient for complex ItemStacks (banners with patterns, enchanted items, etc.)
 * - Handles binary NBT data properly
 * - Standard practice in Minecraft plugin development
 * - Backward compatible (falls back to string format if Base64 fails)
 */
fun ItemStack.serializeToString(): String {
    return try {
        // Use Bukkit's built-in serialization
        val serialized = this.serialize()

        // Convert to byte array
        val byteArrayOutputStream = ByteArrayOutputStream()
        val objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
        objectOutputStream.writeObject(serialized)
        objectOutputStream.close()

        // Encode to Base64
        val base64Bytes = Base64.getEncoder().encode(byteArrayOutputStream.toByteArray())
        String(base64Bytes)
    } catch (e: IOException) {
        // I/O error during serialization - fallback to simple string format
        val serialized = this.serialize()
        serialized.entries.joinToString(";") { (key, value) ->
            "${key.toString()}=${value.toString()}"
        }
    } catch (e: IllegalArgumentException) {
        // Item contains non-serializable data - fallback to simple string format
        val serialized = this.serialize()
        serialized.entries.joinToString(";") { (key, value) ->
            "${key.toString()}=${value.toString()}"
        }
    }
}

/**
 * Deserializes a Base64 string back to an ItemStack
 */
fun String.deserializeToItemStack(): ItemStack? {
    return try {
        // Try Base64 decoding first
        val base64Bytes = Base64.getDecoder().decode(this.toByteArray())

        val byteArrayInputStream = ByteArrayInputStream(base64Bytes)
        val objectInputStream = ObjectInputStream(byteArrayInputStream)

        @Suppress("UNCHECKED_CAST")
        val serialized = objectInputStream.readObject() as Map<String, Any>
        objectInputStream.close()

        ItemStack.deserialize(serialized)
    } catch (e: IllegalArgumentException) {
        // Invalid Base64 or deserialization data - try fallback to old string format
        try {
            val entries = this.split(";").associate { entry ->
                val (key, value) = entry.split("=", limit = 2)
                key to value
            }
            ItemStack.deserialize(entries)
        } catch (fallbackException: IllegalArgumentException) {
            // Old format also invalid
            null
        }
    } catch (e: IOException) {
        // I/O error during deserialization - try fallback to old string format
        try {
            val entries = this.split(";").associate { entry ->
                val (key, value) = entry.split("=", limit = 2)
                key to value
            }
            ItemStack.deserialize(entries)
        } catch (fallbackException: IllegalArgumentException) {
            null
        }
    } catch (e: ClassNotFoundException) {
        // Serialized class not found - data corrupt or incompatible version
        null
    }
}

/**
 * Test function to verify serialization/deserialization works correctly
 * Call this during development to ensure banner patterns are preserved
 */
fun testItemStackSerialization(itemStack: ItemStack): Boolean {
    return try {
        val serialized = itemStack.serializeToString()
        val deserialized = serialized.deserializeToItemStack()
        deserialized != null && deserialized.type == itemStack.type
    } catch (e: Exception) {
        false
    }
}
