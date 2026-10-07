package net.lumalyte.lg.infrastructure.enthusiastaff

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginEnableEvent

internal class EnthusiaStaffEnableListener(
    private val onEnabled: () -> Unit,
) : Listener {
    @EventHandler
    fun onPluginEnable(event: PluginEnableEvent) {
        if (event.plugin.name.equals("EnthusiaStaff", ignoreCase = true)) {
            onEnabled()
        }
    }
}
