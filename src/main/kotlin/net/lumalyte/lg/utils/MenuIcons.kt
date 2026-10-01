package net.lumalyte.lg.utils

import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * Shared icon helpers so the same kind of control looks and behaves the same in every guild menu.
 */
object MenuIcons {

    /**
     * Inbox-style button for pending requests or declarations.
     *
     * - Empty: a dimmed version of the same icon, so the button keeps its identity instead of
     *   turning into an anonymous gray dye.
     * - Not empty: the full icon, with the stack count showing how many are waiting (capped at 64).
     */
    fun requests(incoming: Boolean, count: Int): ItemStack {
        val base = if (incoming) "lg_requests_in" else "lg_requests_out"
        val id = if (count == 0) "${base}_empty" else base
        val fallback = when {
            count == 0 -> Material.GRAY_DYE
            incoming -> Material.PAPER
            else -> Material.WRITABLE_BOOK
        }
        return withCount(NexoItemProvider.getItemStackOrFallback(id) { ItemStack.of(fallback) }, count)
    }

    /**
     * Uses the item's stack size as a number badge. Works on Java and Bedrock without any resource pack.
     */
    fun withCount(item: ItemStack, count: Int): ItemStack {
        if (count <= 1) return item
        val shown = count.coerceAtMost(64)
        if (item.maxStackSize < shown) {
            val meta = item.itemMeta
            if (meta != null) {
                meta.setMaxStackSize(shown)
                item.itemMeta = meta
            }
        }
        item.amount = shown
        return item
    }
}
