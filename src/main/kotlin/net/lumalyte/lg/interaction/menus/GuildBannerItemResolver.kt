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

    private fun isPhysicalBanner(item: ItemStack): Boolean =
        item.type.name.endsWith("_BANNER") &&
            !item.type.name.endsWith("_WALL_BANNER")
}
