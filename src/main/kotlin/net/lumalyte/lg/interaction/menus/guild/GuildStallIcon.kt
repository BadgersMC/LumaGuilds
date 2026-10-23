package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.NexoItemProvider
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/** Semantic menu art with the existing provider's vanilla fallback contract. */
internal enum class GuildStallIcon(val itemId: String, private val material: Material) {
    STALL("lg_nav_stalls", Material.OAK_SIGN),
    MEMBER("lg_nav_members", Material.PLAYER_HEAD),
    BACK("lg_back", Material.ARROW),
    PREVIOUS("lg_page_prev", Material.ARROW),
    REFRESH("lg_stats_refresh", Material.COMPASS),
    NEXT("lg_page_next", Material.ARROW),
    STATUS("lg_nav_stalls", Material.OAK_SIGN),
    SALES("lg_history", Material.WRITABLE_BOOK),
    ACCESS("lg_party_permissions", Material.TRIPWIRE_HOOK),
    ;

    fun item(): ItemStack = NexoItemProvider.getItemStackOrFallback(itemId) { ItemStack.of(material) }
}
