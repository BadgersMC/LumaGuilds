package net.lumalyte.lg.utils

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * Holiday menu styles ([GuiTheme.seasonalIcons]) re-skin the LumaGuilds menu icons (REQ-121).
 * Every Nexo menu icon is tagged with its base Nexo id; for members of a guild using a seasonal
 * style, [net.lumalyte.lg.infrastructure.services.MenuIconAdapter] sends the model of the
 * `<id>_<style>` Nexo item instead (e.g. `lg_back_halloween`). Icons without a variant keep
 * their normal look, so a style can ship a partial icon set.
 */
object SeasonalIcons {
    /** PDC key holding the base Nexo icon id. Stored as `lumaguilds:icon`. */
    val ICON_KEY = NamespacedKey("lumaguilds", "icon")

    /** [ICON_KEY] as it appears inside [BedrockIcons.PDC_ROOT] in raw item data. */
    const val PDC_KEY = "lumaguilds:icon"

    /** Nexo id of [iconId]'s variant for [theme], or null when the theme keeps the normal icons. */
    fun variantId(iconId: String, theme: GuiTheme): String? =
        if (theme.seasonalIcons) "${iconId}_${theme.name.lowercase()}" else null

    /** The seasonal style a member of guilds using [themes] sees, or null for the normal icons. */
    fun styleFor(themes: Collection<GuiTheme>): GuiTheme? =
        themes.map(GuiTheme::resolved).firstOrNull(GuiTheme::seasonalIcons)

    /** Marks [item] as the menu icon [iconId]. */
    fun tag(item: ItemStack, iconId: String): ItemStack {
        item.editMeta { it.persistentDataContainer.set(ICON_KEY, PersistentDataType.STRING, iconId) }
        return item
    }

    /** The base icon id [item] was tagged with, or null for anything else. */
    fun iconId(item: ItemStack): String? =
        item.takeIf { it.hasItemMeta() }?.itemMeta?.persistentDataContainer?.get(ICON_KEY, PersistentDataType.STRING)

    /** A copy of [item] (same name, lore, count and tags) drawn with [variant]'s model. */
    fun restyle(item: ItemStack, variant: ItemStack): ItemStack {
        val look = variant.itemMeta ?: return item
        return item.clone().also { copy ->
            copy.editMeta { meta ->
                meta.setItemModel(look.itemModel)
                meta.setCustomModelDataComponent(look.customModelDataComponent)
            }
        }
    }
}
