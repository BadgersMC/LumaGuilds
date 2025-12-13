package net.lumalyte.lg.interaction.commands.admin

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.VaultBackupService
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Admin command to list and restore vault backups.
 *
 * Usage:
 *   /vaultrollback list <guild>
 *   /vaultrollback restore <guild> <backupId>
 */
class VaultRollbackCommand(
    private val guildService: GuildService,
    private val backupService: VaultBackupService
) : CommandExecutor, TabCompleter {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cThis command can only be used by players")
            return true
        }

        if (!sender.hasPermission("lumaguilds.admin.vault.rollback")) {
            sender.sendMessage("§cYou don't have permission to use this command")
            return true
        }

        if (args.isEmpty()) {
            sendUsage(sender)
            return true
        }

        when (args[0].lowercase()) {
            "list" -> handleList(sender, args)
            "restore" -> handleRestore(sender, args)
            else -> sendUsage(sender)
        }

        return true
    }

    private fun handleList(sender: Player, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("§cUsage: /vaultrollback list <guildName>")
            return
        }

        val guildName = args[1]
        val guild = guildService.getGuildByName(guildName)

        if (guild == null) {
            sender.sendMessage("§cGuild '$guildName' not found")
            return
        }

        val backups = backupService.listBackups(guild.id)

        if (backups.isEmpty()) {
            sender.sendMessage("§eNo backups found for guild '$guildName'")
            return
        }

        sender.sendMessage("§6§l━━━ Vault Backups: ${guild.name} ━━━")
        sender.sendMessage("§7Total: §f${backups.size} backups")
        sender.sendMessage("")

        backups.sortedByDescending { it.timestamp }.take(10).forEach { backup ->
            val timestamp = dateFormatter.format(backup.timestamp)
            sender.sendMessage(
                "§e${backup.backupId.substringAfterLast('-')}" +
                " §7| §f$timestamp" +
                " §7| §b${backup.itemCount} items" +
                " §7| §7${backup.reason}"
            )
        }

        if (backups.size > 10) {
            sender.sendMessage("§7... and ${backups.size - 10} more (showing most recent 10)")
        }

        sender.sendMessage("")
        sender.sendMessage("§7Use §e/vaultrollback restore ${guild.name} <backupId>§7 to restore")
    }

    private fun handleRestore(sender: Player, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage("§cUsage: /vaultrollback restore <guildName> <backupId>")
            return
        }

        val guildName = args[1]
        val backupId = args[2]

        val guild = guildService.getGuildByName(guildName)

        if (guild == null) {
            sender.sendMessage("§cGuild '$guildName' not found")
            return
        }

        // Validate backup exists
        val backups = backupService.listBackups(guild.id)
        val backup = backups.find { it.backupId.endsWith(backupId) || it.backupId == backupId }

        if (backup == null) {
            sender.sendMessage("§cBackup '$backupId' not found for guild '$guildName'")
            sender.sendMessage("§7Use §e/vaultrollback list $guildName§7 to see available backups")
            return
        }

        sender.sendMessage("§e⚠ Restoring vault backup...")
        sender.sendMessage("§7Backup: §f${backup.backupId}")
        sender.sendMessage("§7Created: §f${dateFormatter.format(backup.timestamp)}")
        sender.sendMessage("§7Items: §f${backup.itemCount}")

        val success = backupService.restoreBackup(guild.id, backup.backupId, sender.uniqueId)

        if (success) {
            sender.sendMessage("§a✓ Vault restored successfully!")
            sender.sendMessage("§7All players viewing this vault will see updated contents")
        } else {
            sender.sendMessage("§c✗ Failed to restore vault")
            sender.sendMessage("§7Check server logs for details")
        }
    }

    private fun sendUsage(sender: Player) {
        sender.sendMessage("§6§lVault Rollback Command")
        sender.sendMessage("§e/vaultrollback list <guild>§7 - List available backups")
        sender.sendMessage("§e/vaultrollback restore <guild> <backupId>§7 - Restore a backup")
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (!sender.hasPermission("lumaguilds.admin.vault.rollback")) {
            return emptyList()
        }

        return when (args.size) {
            1 -> listOf("list", "restore").filter { it.startsWith(args[0].lowercase()) }
            2 -> {
                // Guild name suggestions would go here
                emptyList()
            }
            else -> emptyList()
        }
    }
}
