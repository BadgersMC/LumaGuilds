package net.lumalyte.lg.infrastructure.hytale.adapters

import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.asset.type.item.config.Item as HytaleItem
import net.lumalyte.lg.domain.values.Item
import org.bson.BsonDocument
import org.bson.BsonString
import org.bson.BsonArray
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("HytaleItemAdapter")

/**
 * Converts Hytale ItemStack to domain Item
 */
fun ItemStack.toItem(): Item {
    // Extract display name from metadata
    val displayName = this.metadata?.get("DisplayName")?.let {
        (it as? BsonString)?.value
    }

    // Extract lore from metadata
    val loreList = this.metadata?.get("Lore")?.let { bsonValue ->
        (bsonValue as? BsonArray)?.mapNotNull { (it as? BsonString)?.value } ?: emptyList()
    } ?: emptyList()

    return Item(
        type = this.itemId,
        amount = this.quantity,
        displayName = displayName,
        lore = loreList,
        metadata = emptyMap(), // TODO: Parse other metadata if needed
        enchantments = emptyMap(), // TODO: Extract enchantments if Hytale supports them
        customModelData = null,
        nbtData = emptyMap()
    )
}

/**
 * Converts domain Item to Hytale ItemStack
 * Returns null if the item type doesn't exist in Hytale's registry
 */
fun Item.toHytaleItemStack(): ItemStack? {
    // Validate item exists
    val hytaleItem = HytaleItem.getAssetMap().getAsset(this.type)
    if (hytaleItem == null) {
        log.warn("Item type '${this.type}' not found in Hytale registry")
        return null
    }

    // Build metadata if we have display name or lore
    val metadata = if (displayName != null || lore.isNotEmpty()) {
        BsonDocument().apply {
            displayName?.let { put("DisplayName", BsonString(it)) }
            if (lore.isNotEmpty()) {
                put("Lore", BsonArray(lore.map { BsonString(it) }))
            }
        }
    } else null

    return ItemStack(this.type, this.amount, metadata)
}

/**
 * Converts a list of nullable Hytale ItemStacks to domain Items
 * Filters out nulls and empty stacks
 */
fun List<ItemStack?>.toItems(): List<Item> {
    return this.mapNotNull { stack ->
        if (stack == null || stack.isEmpty) null
        else stack.toItem()
    }
}

/**
 * Converts a list of domain Items to Hytale ItemStacks
 * Filters out items that don't exist in Hytale's registry
 */
fun List<Item>.toHytaleItemStacks(): List<ItemStack> {
    return this.mapNotNull { it.toHytaleItemStack() }
}
