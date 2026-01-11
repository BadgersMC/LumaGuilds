package net.lumalyte.lg.domain.values

import java.util.*

/**
 * Platform-agnostic representation of an item.
 * This abstraction allows the domain layer to work with items
 * without depending on any specific game engine (Bukkit, Hytale, etc.)
 */
data class Item(
    val type: String,
    val amount: Int = 1,
    val displayName: String? = null,
    val lore: List<String> = emptyList(),
    val metadata: Map<String, Any> = emptyMap(),
    val enchantments: Map<String, Int> = emptyMap(),
    val customModelData: Int? = null,
    val nbtData: Map<String, Any> = emptyMap()
) {
    init {
        require(amount > 0) { "Item amount must be positive" }
        require(type.isNotBlank()) { "Item type cannot be blank" }
    }

    companion object {
        /**
         * Creates an empty/air item
         */
        fun empty(): Item = Item(type = "AIR", amount = 0)

        /**
         * Creates a simple item with just a type and amount
         */
        fun of(type: String, amount: Int = 1): Item = Item(type = type, amount = amount)
    }

    /**
     * Returns true if this item is empty (air or zero amount)
     */
    fun isEmpty(): Boolean = type == "AIR" || amount <= 0

    /**
     * Returns true if this item has a non-empty value
     */
    fun isNotEmpty(): Boolean = !isEmpty()

    /**
     * Creates a copy of this item with a different amount
     */
    fun withAmount(newAmount: Int): Item = copy(amount = newAmount)

    /**
     * Creates a copy of this item with additional metadata
     */
    fun withMetadata(key: String, value: Any): Item {
        val newMetadata = metadata.toMutableMap()
        newMetadata[key] = value
        return copy(metadata = newMetadata)
    }

    /**
     * Creates a copy of this item with a display name
     */
    fun withDisplayName(name: String): Item = copy(displayName = name)

    /**
     * Creates a copy of this item with lore
     */
    fun withLore(vararg lines: String): Item = copy(lore = lines.toList())
}
