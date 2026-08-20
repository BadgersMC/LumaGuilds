package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService
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

    private val lang: LangService by inject()
    private val formCacheService: FormCacheService by inject()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // Check permissions
        if (!sender.hasPermission("lumaguilds.bedrock.cache.stats")) {
            sender.sendMessage(lang.msg("admin.migrated.bedrock_cache_stats.command.you_don_t_have_permission_to_view"))
            return true
        }

        when (args.getOrNull(0)?.lowercase()) {
            "stats", null -> showStats(sender)
            "clear" -> clearCache(sender)
            "help" -> showHelp(sender)
            else -> {
                sender.sendMessage(lang.msg("admin.migrated.bedrock_cache_stats.command.unknown_subcommand_use_bedrockcachestats_help_for_available"))
            }
        }

        return true
    }

    private fun showStats(sender: CommandSender) {
        val stats = formCacheService.getCacheStats()

        sender.sendMessage(lang.msg("admin.migrated.bedrock_cache_stats.showstats.bedrock_form_cache_statistics"))
        sender.sendMessage(lang.msg("admin.migrated.bedrock_cache_stats.showstats.cache_size", "cache_size" to stats.cacheSize, "max_size" to stats.maxSize))
        sender.sendMessage(lang.msg("admin.migrated.bedrock_cache_stats.showstats.hit_rate", "hit_rate" to String.format("%.1f", stats.hitRate * 100)))
        sender.sendMessage(lang.msg("admin.migrated.bedrock_cache_stats.showstats.cache_hits", "hit_count" to stats.hitCount))
        sender.sendMessage(lang.msg("admin.migrated.bedrock_cache_stats.showstats.cache_misses", "miss_count" to stats.missCount))
        sender.sendMessage(lang.msg("admin.migrated.bedrock_cache_stats.showstats.evictions", "evictions" to stats.evictions))

        if (sender is Player) {
            // Show additional info for players
            sender.sendMessage(lang.msg("admin.migrated.bedrock_cache_stats.showstats.use_bedrockcachestats_clear_to_clear_the_cache"))
        }
    }

    private fun clearCache(sender: CommandSender) {
        if (!sender.hasPermission("lumaguilds.bedrock.cache.clear")) {
            sender.sendMessage(lang.msg("admin.migrated.bedrock_cache_stats.clearcache.you_don_t_have_permission_to_clear"))
            return
        }

        val oldStats = formCacheService.getCacheStats()
        formCacheService.clearCache()
        val newStats = formCacheService.getCacheStats()

        sender.sendMessage(lang.msg("admin.migrated.bedrock_cache_stats.clearcache.cache_cleared_successfully"))
        sender.sendMessage(lang.msg("admin.migrated.bedrock_cache_stats.clearcache.cleared_cached_forms", "cache_size" to oldStats.cacheSize))
        sender.sendMessage(lang.msg("admin.migrated.bedrock_cache_stats.clearcache.cache_size_is_now", "cache_size" to newStats.cacheSize, "max_size" to newStats.maxSize))
    }

    private fun showHelp(sender: CommandSender) {
        sender.sendMessage(lang.msg("admin.migrated.bedrock_cache_stats.showhelp.bedrock_cache_stats_commands"))
        sender.sendMessage(lang.msg("admin.migrated.bedrock_cache_stats.showhelp.bedrockcachestats_show_cache_statistics"))
        sender.sendMessage(lang.msg("admin.migrated.bedrock_cache_stats.showhelp.bedrockcachestats_clear_clear_all_cached_forms_requires"))

        if (sender.hasPermission("lumaguilds.bedrock.cache.stats")) {
            sender.sendMessage(lang.msg("admin.migrated.bedrock_cache_stats.showhelp.you_have_permission_to_view_stats"))
        } else {
            sender.sendMessage(lang.msg("admin.migrated.bedrock_cache_stats.showhelp.you_don_t_have_permission_to_view"))
        }

        if (sender.hasPermission("lumaguilds.bedrock.cache.clear")) {
            sender.sendMessage(lang.msg("admin.migrated.bedrock_cache_stats.showhelp.you_have_permission_to_clear_cache"))
        } else {
            sender.sendMessage(lang.msg("admin.migrated.bedrock_cache_stats.showhelp.you_don_t_have_permission_to_clear"))
        }
    }
}
