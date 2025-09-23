package net.lumalyte.lg.interaction.commands

import net.lumalyte.lg.application.services.FormCacheService
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Command to view Bedrock form cache statistics
 * Usage: /bedrockcachestats
 */
class BedrockCacheStatsCommand : CommandExecutor, KoinComponent {

    private val formCacheService: FormCacheService by inject()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // Check permissions
        if (!sender.hasPermission("lumalyte.bedrock.cache.stats")) {
            sender.sendMessage("§cYou don't have permission to view cache statistics.")
            return true
        }

        when (args.getOrNull(0)?.lowercase()) {
            "stats", null -> showStats(sender)
            "clear" -> clearCache(sender)
            "help" -> showHelp(sender)
            else -> {
                sender.sendMessage("§cUnknown subcommand. Use /bedrockcachestats help for available commands.")
            }
        }

        return true
    }

    private fun showStats(sender: CommandSender) {
        val stats = formCacheService.getCacheStats()

        sender.sendMessage("§6=== Bedrock Form Cache Statistics ===")
        sender.sendMessage("§eCache Size: §f${stats.cacheSize}/${stats.maxSize}")
        sender.sendMessage("§eHit Rate: §f${String.format("%.1f", stats.hitRate * 100)}%")
        sender.sendMessage("§eCache Hits: §f${stats.hitCount}")
        sender.sendMessage("§eCache Misses: §f${stats.missCount}")
        sender.sendMessage("§eEvictions: §f${stats.evictions}")

        if (sender is Player) {
            // Show additional info for players
            sender.sendMessage("§7Use §f/bedrockcachestats clear §7to clear the cache")
        }
    }

    private fun clearCache(sender: CommandSender) {
        if (!sender.hasPermission("lumalyte.bedrock.cache.clear")) {
            sender.sendMessage("§cYou don't have permission to clear the cache.")
            return
        }

        val oldStats = formCacheService.getCacheStats()
        formCacheService.clearCache()
        val newStats = formCacheService.getCacheStats()

        sender.sendMessage("§aCache cleared successfully!")
        sender.sendMessage("§7Cleared ${oldStats.cacheSize} cached forms")
        sender.sendMessage("§7Cache size is now: ${newStats.cacheSize}/${newStats.maxSize}")
    }

    private fun showHelp(sender: CommandSender) {
        sender.sendMessage("§6=== Bedrock Cache Stats Commands ===")
        sender.sendMessage("§e/bedrockcachestats §7- Show cache statistics")
        sender.sendMessage("§e/bedrockcachestats clear §7- Clear all cached forms (requires clear permission)")

        if (sender.hasPermission("lumalyte.bedrock.cache.stats")) {
            sender.sendMessage("§aYou have permission to view stats")
        } else {
            sender.sendMessage("§cYou don't have permission to view stats")
        }

        if (sender.hasPermission("lumalyte.bedrock.cache.clear")) {
            sender.sendMessage("§aYou have permission to clear cache")
        } else {
            sender.sendMessage("§cYou don't have permission to clear cache")
        }
    }
}
