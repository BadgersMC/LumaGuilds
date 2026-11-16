package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.NexoItemProvider
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * REQ-269: the prestige badge (Enthusia Pack lg_prestige_1..6) for a tier, in place of the generic
 * prestige diamond. Tier 0, or a pack without the badges, keeps the diamond.
 */
internal object PrestigeIcon {
    const val GENERIC = "lg_prestige"
    const val MAX_BADGE = 6

    fun id(tier: Int): String = if (tier <= 0) GENERIC else "${GENERIC}_${tier.coerceAtMost(MAX_BADGE)}"

    fun item(tier: Int): ItemStack = NexoItemProvider.getItemStackOrFallback(id(tier)) { generic() }

    fun generic(): ItemStack = NexoItemProvider.getItemStackOrFallback(GENERIC) { ItemStack.of(Material.NETHER_STAR) }
}
