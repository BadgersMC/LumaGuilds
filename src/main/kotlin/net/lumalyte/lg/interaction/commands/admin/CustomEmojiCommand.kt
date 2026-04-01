package net.lumalyte.lg.interaction.commands.admin

import net.lumalyte.lg.application.services.GuildService
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

/**
 * Admin command: /customemoji <guildName> <emoji|clear>
 *
 * Grants a custom emoji to a Level 100 guild. Bypasses tier system.
 * Permission: lumaguilds.admin.emoji.custom
 */
class CustomEmojiCommand(
    private val guildService: GuildService
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("lumaguilds.admin.emoji.custom")) {
            sender.sendMessage("§cYou don't have permission to use this command.")
            return true
        }
        if (args.size < 2) {
            sender.sendMessage("§cUsage: /customemoji <guildName> <emoji|clear>")
            return true
        }

        val guildName = args[0]
        val emojiArg = args[1]

        val guild = guildService.getGuildByName(guildName)
        if (guild == null) {
            sender.sendMessage("§cGuild '${guildName}' not found.")
            return true
        }

        if (guild.level < 100) {
            sender.sendMessage("§c❌ '${guildName}' is Level ${guild.level}. Custom emojis require Level 100.")
            return true
        }

        val emoji = if (emojiArg.equals("clear", ignoreCase = true)) null else emojiArg

        val success = guildService.setEmojiAdmin(guild.id, emoji)
        if (success) {
            if (emoji == null) sender.sendMessage("§a✅ Custom emoji cleared for '${guildName}'.")
            else sender.sendMessage("§a✅ Custom emoji '${emoji}' set for '${guildName}'.")
        } else {
            sender.sendMessage("§c❌ Failed to set emoji. Check the emoji format (:name:).")
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> guildService.getAllGuilds().map { it.name }.filter { it.startsWith(args[0], ignoreCase = true) }
            else -> emptyList()
        }
    }
}
