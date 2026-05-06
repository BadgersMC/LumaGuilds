package net.lumalyte.lg.interaction.listeners

import net.lumalyte.lg.common.PluginKeys
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * Prevents the special "guild vault" chest item (marked with GUILD_VAULT_ID) from
 * being consumed in crafting recipes (e.g. boat-with-chest, hopper, trapped chest).
 * Without this, players could trade an unlimited supply of cheap recipe outputs for
 * the labelled vault chest received from /guild getvault.
 */
class GuildVaultCraftingPreventionListener : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onPrepareItemCraft(event: PrepareItemCraftEvent) {
        val matrix = event.inventory.matrix
        if (matrix.any { it.isGuildVaultItem() }) {
            event.inventory.result = ItemStack(Material.AIR)
        }
    }

    private fun ItemStack?.isGuildVaultItem(): Boolean {
        if (this == null) return false
        val meta = this.itemMeta ?: return false
        return meta.persistentDataContainer.has(
            PluginKeys.GUILD_VAULT_ID,
            PersistentDataType.STRING
        )
    }
}
