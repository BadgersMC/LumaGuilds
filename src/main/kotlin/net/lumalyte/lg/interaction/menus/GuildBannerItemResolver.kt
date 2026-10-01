package net.lumalyte.lg.interaction.menus

import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.utils.deserializeToItemStack
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object GuildBannerItemResolver {
    fun resolve(guild: Guild): ItemStack =
        guild.banner
            ?.let { encoded ->
                runCatching { encoded.deserializeToItemStack() }.getOrNull()
            }
            ?.takeIf(::isPhysicalBanner)
            ?.clone()
            ?: ItemStack.of(Material.WHITE_BANNER)

    /**
     * The guild's own banner for display in a menu slot: the stored banner (base colour and
     * every pattern layer) with any pack model override removed, so it renders as the real
     * vanilla banner players see on the guild's banner block. Falls back to a plain white
     * banner only when the guild has not set one.
     */
    fun resolveForDisplay(guild: Guild): ItemStack {
        val item = resolve(guild)
        val meta = item.itemMeta ?: return item
        meta.setItemModel(null)
        meta.setCustomModelDataComponent(null)
        item.itemMeta = meta
        return item
    }

    private fun isPhysicalBanner(item: ItemStack): Boolean =
        item.type.name.endsWith("_BANNER") &&
            !item.type.name.endsWith("_WALL_BANNER")
}
