package net.lumalyte.lg.utils

import com.github.sirblobman.combatlogx.api.ICombatLogX
import org.bukkit.Bukkit
import org.bukkit.entity.Player

object CombatUtil {
    fun isEnabled(): Boolean {
        val pluginManager = Bukkit.getPluginManager()
        return pluginManager.isPluginEnabled("CombatLogX")
    }

    fun getAPI(): ICombatLogX? {
        val pluginManager = Bukkit.getPluginManager()
        val plugin = pluginManager.getPlugin("CombatLogX")
        return plugin as ICombatLogX?
    }

    fun isInCombat(player: Player): Boolean {
        val plugin: ICombatLogX = getAPI() ?: return false
        val combatManager = plugin.getCombatManager()
        return combatManager.isInCombat(player)
    }
}