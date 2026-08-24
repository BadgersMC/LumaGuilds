package net.lumalyte.lg.interaction.commands.admin

import net.badgersmc.nexus.i18n.LangService
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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

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
) : CommandExecutor, TabCompleter, KoinComponent {

    private val lang: LangService by inject()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage(lang.msg("admin.common.player_only"))
            return true
        }

        if (!sender.hasPermission("lumaguilds.admin.vault.rollback")) {
            sender.sendMessage(lang.msg("admin.common.no_permission"))
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
            sender.sendMessage(lang.msg("admin.migrated.vault_rollback.handlelist.usage_vaultrollback_list_guildname"))
            return
        }

        val guildName = args[1]
        val guild = net.lumalyte.lg.utils.GuildResolver.resolveGuildByName(guildName, guildService)

        if (guild == null) {
            sender.sendMessage(lang.msg("admin.migrated.remove_vault.command.guild_not_found", "guild" to guildName))
            return
        }

        val backups = backupService.listBackups(guild.id)

        if (backups.isEmpty()) {
            sender.sendMessage(lang.msg("admin.migrated.vault_rollback.handlelist.no_backups_found_for_guild", "guild" to guildName))
            return
        }

        sender.sendMessage(lang.msg("admin.migrated.vault_rollback.handlelist.vault_backups", "guild" to guild.name))
        sender.sendMessage(lang.msg("admin.migrated.vault_rollback.handlelist.total_backups", "size" to backups.size))
        sender.sendMessage(lang.msg("command.common.blank_line"))

        backups.sortedByDescending { it.timestamp }.take(10).forEach { backup ->
            val timestamp = dateFormatter.format(backup.timestamp)
            sender.sendMessage(
                lang.msg(
                    "admin.migrated.vault_rollback.handlelist.row",
                    "backup_id" to backup.backupId.substringAfterLast('-'),
                    "timestamp" to timestamp,
                    "item_count" to backup.itemCount,
                    "reason" to backup.reason,
                ),
            )
        }

        if (backups.size > 10) {
            sender.sendMessage(lang.msg("admin.migrated.vault_rollback.handlelist.and_more_showing_most_recent_10", "size" to backups.size - 10))
        }

        sender.sendMessage(lang.msg("command.common.blank_line"))
        sender.sendMessage(lang.msg("admin.migrated.vault_rollback.handlelist.use_vaultrollback_restore_backupid_to_restore", "guild" to guild.name))
    }

    private fun handleRestore(sender: Player, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage(lang.msg("admin.migrated.vault_rollback.handlerestore.usage_vaultrollback_restore_guildname_backupid"))
            return
        }

        val guildName = args[1]
        val backupId = args[2]

        val guild = net.lumalyte.lg.utils.GuildResolver.resolveGuildByName(guildName, guildService)

        if (guild == null) {
            sender.sendMessage(lang.msg("admin.migrated.remove_vault.command.guild_not_found", "guild" to guildName))
            return
        }

        // Validate backup exists
        val backups = backupService.listBackups(guild.id)
        val backup = backups.find { it.backupId.endsWith(backupId) || it.backupId == backupId }

        if (backup == null) {
            sender.sendMessage(lang.msg("admin.migrated.vault_rollback.handlerestore.backup_not_found_for_guild", "backup_id" to backupId, "guild" to guildName))
            sender.sendMessage(lang.msg("admin.migrated.vault_rollback.handlerestore.use_vaultrollback_list_to_see_available_backups", "guild" to guildName))
            return
        }

        sender.sendMessage(lang.msg("admin.migrated.vault_rollback.handlerestore.restoring_vault_backup"))
        sender.sendMessage(lang.msg("admin.migrated.vault_rollback.handlerestore.backup", "backup_id" to backup.backupId))
        sender.sendMessage(lang.msg("admin.migrated.vault_rollback.handlerestore.created", "timestamp" to dateFormatter.format(backup.timestamp)))
        sender.sendMessage(lang.msg("admin.migrated.vault_rollback.handlerestore.items", "item_count" to backup.itemCount))

        val success = backupService.restoreBackup(guild.id, backup.backupId, sender.uniqueId)

        if (success) {
            sender.sendMessage(lang.msg("admin.migrated.vault_rollback.handlerestore.vault_restored_successfully"))
            sender.sendMessage(lang.msg("admin.migrated.vault_rollback.handlerestore.all_players_viewing_this_vault_will_see"))
        } else {
            sender.sendMessage(lang.msg("admin.migrated.vault_rollback.handlerestore.failed_to_restore_vault"))
            sender.sendMessage(lang.msg("admin.migrated.remove_vault.command.check_server_logs_for_details"))
        }
    }

    private fun sendUsage(sender: Player) {
        sender.sendMessage(lang.msg("admin.migrated.vault_rollback.sendusage.vault_rollback_command"))
        sender.sendMessage(lang.msg("admin.migrated.vault_rollback.sendusage.vaultrollback_list_guild_list_available_backups"))
        sender.sendMessage(lang.msg("admin.migrated.vault_rollback.sendusage.vaultrollback_restore_guild_backupid_restore_a_backup"))
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
            2 -> net.lumalyte.lg.utils.GuildResolver.suggestions(guildService)
                .filter { it.startsWith(args[1], ignoreCase = true) }
            else -> emptyList()
        }
    }
}
