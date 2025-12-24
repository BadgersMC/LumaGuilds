package net.lumalyte.lg.interaction.commands.admin

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.GuildVaultService
import net.lumalyte.lg.application.services.VaultResult
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.slf4j.LoggerFactory

/**
 * Admin command to forcibly remove a guild's vault.
 *
 * Usage: /removevault <guild> [dropItems]
 */
class RemoveVaultCommand(
    private val guildService: GuildService,
    private val vaultService: GuildVaultService
) : CommandExecutor, TabCompleter {

    private val logger = LoggerFactory.getLogger(RemoveVaultCommand::class.java)

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (!sender.hasPermission("lumaguilds.admin.vault.remove")) {
            sender.sendMessage("§cYou don't have permission to use this command")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("§cUsage: /removevault <guildName> [dropItems]")
            sender.sendMessage("§7dropItems: true/false (default: true)")
            return true
        }

        val guildName = args[0]
        val dropItems = if (args.size > 1) {
            args[1].equals("true", ignoreCase = true)
        } else {
            true // Default to dropping items
        }

        // Find guild
        val guild = guildService.getGuildByName(guildName)
        if (guild == null) {
            sender.sendMessage("§cGuild '$guildName' not found")
            return true
        }

        // Check if guild has a vault
        if (guild.vaultChestLocation == null) {
            sender.sendMessage("§cGuild '${guild.name}' does not have a vault")
            return true
        }

        sender.sendMessage("§e⚠ Removing vault for guild §f${guild.name}§e...")
        sender.sendMessage("§7Vault location: §f${guild.vaultChestLocation}")
        sender.sendMessage("§7Drop items: §f$dropItems")

        // Remove the vault
        val result = vaultService.removeVaultChest(guild, dropItems)

        when (result) {
            is VaultResult.Success -> {
                sender.sendMessage("§a✓ Successfully removed vault for guild §f${guild.name}")
                if (dropItems) {
                    sender.sendMessage("§7Items have been dropped at the vault location")
                } else {
                    sender.sendMessage("§7Items have been §c§lDELETED §7(not dropped)")
                }
                logger.info("Admin ${sender.name} forcibly removed vault for guild ${guild.name} (dropItems=$dropItems)")
            }
            is VaultResult.Failure -> {
                sender.sendMessage("§c✗ Failed to remove vault: ${result.message}")
                sender.sendMessage("§7Check server logs for details")
                logger.error("Failed to remove vault for guild ${guild.name}: ${result.message}")
            }
        }

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (!sender.hasPermission("lumaguilds.admin.vault.remove")) {
            return emptyList()
        }

        return when (args.size) {
            1 -> {
                // Guild name suggestions would go here
                // For now, return empty list
                emptyList()
            }
            2 -> {
                listOf("true", "false").filter { it.startsWith(args[1], ignoreCase = true) }
            }
            else -> emptyList()
        }
    }
}
