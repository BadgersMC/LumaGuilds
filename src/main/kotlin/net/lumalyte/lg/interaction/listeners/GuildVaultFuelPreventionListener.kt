package net.lumalyte.lg.interaction.listeners

import net.lumalyte.lg.common.PluginKeys
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.FurnaceBurnEvent
import org.bukkit.persistence.PersistentDataType

/**
 * Prevents guild vault chests (marked with GUILD_VAULT_ID) from being used as furnace fuel.
 * Without this, chests with the guild-vault tag would still burn as ordinary chest fuel.
 */
class GuildVaultFuelPreventionListener : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onFurnaceBurn(event: FurnaceBurnEvent) {
        val fuel = event.fuel ?: return

        val meta = fuel.itemMeta ?: return
        val isGuildVault = meta.persistentDataContainer.has(
            PluginKeys.GUILD_VAULT_ID,
            PersistentDataType.STRING
        )

        if (isGuildVault) {
            event.isCancelled = true
        }
    }
}
