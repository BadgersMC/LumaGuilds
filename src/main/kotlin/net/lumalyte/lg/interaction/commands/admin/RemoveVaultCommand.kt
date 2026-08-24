package net.lumalyte.lg.interaction.commands.admin

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.GuildVaultService
import net.lumalyte.lg.application.services.VaultResult
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.slf4j.LoggerFactory
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Admin command to forcibly remove a guild's vault.
 *
 * Usage: /removevault <guild> [dropItems]
 */
class RemoveVaultCommand(
    private val guildService: GuildService,
    private val vaultService: GuildVaultService
) : CommandExecutor, TabCompleter, KoinComponent {

    private val lang: LangService by inject()
    private val logger = LoggerFactory.getLogger(RemoveVaultCommand::class.java)

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (!sender.hasPermission("lumaguilds.admin.vault.remove")) {
            sender.sendMessage(lang.msg("admin.common.no_permission"))
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage(lang.msg("admin.migrated.remove_vault.command.usage_removevault_guildname_dropitems"))
            sender.sendMessage(lang.msg("admin.migrated.remove_vault.command.dropitems_true_false_default_true"))
            return true
        }

        val guildName = args[0]
        val dropItems = if (args.size > 1) {
            args[1].equals("true", ignoreCase = true)
        } else {
            true // Default to dropping items
        }

        // Find guild (resolve handles exact, case-insensitive, and stripped names)
        val guild = net.lumalyte.lg.utils.GuildResolver.resolveGuildByName(guildName, guildService)
        if (guild == null) {
            sender.sendMessage(lang.msg("admin.migrated.remove_vault.command.guild_not_found", "guild" to guildName))
            return true
        }

        // Check if guild has a vault
        if (guild.vaultChestLocation == null) {
            sender.sendMessage(lang.msg("admin.migrated.remove_vault.command.guild_does_not_have_a_vault", "guild" to guild.name))
            return true
        }

        sender.sendMessage(lang.msg("admin.migrated.remove_vault.command.removing_vault_for_guild", "guild" to guild.name))
        sender.sendMessage(lang.msg("admin.migrated.remove_vault.command.vault_location", "vault_chest_location" to guild.vaultChestLocation))
        sender.sendMessage(lang.msg("admin.migrated.remove_vault.command.drop_items", "drop_items" to dropItems))

        // Remove the vault
        val result = vaultService.removeVaultChest(guild, dropItems)

        when (result) {
            is VaultResult.Success -> {
                sender.sendMessage(lang.msg("admin.migrated.remove_vault.command.successfully_removed_vault_for_guild", "guild" to guild.name))
                if (dropItems) {
                    sender.sendMessage(lang.msg("admin.migrated.remove_vault.command.items_have_been_dropped_at_the_vault"))
                } else {
                    sender.sendMessage(lang.msg("admin.migrated.remove_vault.command.items_have_been_deleted_not_dropped"))
                }
                logger.info("Admin ${sender.name} forcibly removed vault for guild ${guild.name} (dropItems=$dropItems)")
            }
            is VaultResult.Failure -> {
                sender.sendMessage(lang.msg("admin.migrated.remove_vault.command.failed_to_remove_vault", "reason" to result.message))
                sender.sendMessage(lang.msg("admin.migrated.remove_vault.command.check_server_logs_for_details"))
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
            1 -> net.lumalyte.lg.utils.GuildResolver.suggestions(guildService)
                .filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> listOf("true", "false").filter { it.startsWith(args[1], ignoreCase = true) }
            else -> emptyList()
        }
    }
}
