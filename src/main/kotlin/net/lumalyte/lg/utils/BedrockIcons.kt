package net.lumalyte.lg.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextReplacementConfig
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * Bedrock players (Geyser/Floodgate) cannot see Nexo item models or font-glyph menu
 * backgrounds. Every Nexo menu icon is tagged with the vanilla material its menu would
 * use without Nexo; [net.lumalyte.lg.infrastructure.services.MenuIconAdapter] swaps
 * tagged icons for that material in what Bedrock players and members of Vanilla-style
 * guilds are sent, and strips the background glyph from themed titles for Bedrock players.
 * Everyone else sees the custom icons.
 */
object BedrockIcons {
    /** PDC key holding the vanilla fallback material name. Stored as `lumaguilds:bedrock_icon`. */
    val FALLBACK_KEY = NamespacedKey("lumaguilds", "bedrock_icon")

    /** Raw item `custom_data` compound that Bukkit stores plugin PDC values under. */
    const val PDC_ROOT = "PublicBukkitValues"

    /** [FALLBACK_KEY] as it appears inside [PDC_ROOT] in raw item data. */
    const val PDC_KEY = "lumaguilds:bedrock_icon"

    private val TITLE_TAGS = Regex("<(?:shift|glyph):[^>]*>")

    /** Marks [item] with the vanilla [fallback] to show Bedrock players. */
    fun tag(item: ItemStack, fallback: Material): ItemStack {
        if (fallback.isAir || !fallback.isItem) return item
        item.editMeta { it.persistentDataContainer.set(FALLBACK_KEY, PersistentDataType.STRING, fallback.name) }
        return item
    }

    /** The Bedrock-safe copy of a tagged icon (same name, lore and count), or null when untagged. */
    fun toBedrock(item: ItemStack): ItemStack? {
        val name =
            item.takeIf { it.hasItemMeta() }
                ?.itemMeta
                ?.persistentDataContainer
                ?.get(FALLBACK_KEY, PersistentDataType.STRING)
        val material = name?.let { Material.getMaterial(it) }?.takeIf { it.isItem && !it.isAir } ?: return null
        return item.withType(material).also { copy ->
            copy.editMeta { meta ->
                meta.setItemModel(null)
                meta.setCustomModelDataComponent(null)
            }
        }
    }

    /** True for titles built by [MenuTitleBuilder] (they carry a guild background glyph). */
    fun isThemedTitle(plainTitle: String): Boolean = plainTitle.contains("<glyph:guild_bg_")

    /** The title with the background glyph and pixel shifts removed, keeping the text's colours. */
    fun plainTitle(title: Component): Component =
        title.replaceText(TextReplacementConfig.builder().match(TITLE_TAGS.toPattern()).replacement("").build())
}
