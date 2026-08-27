package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.services.AdminOverrideService
import net.lumalyte.lg.application.services.GuildRolePermissionResolver
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.infrastructure.persistence.migrations.DatabaseMigrationUtility
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import kotlin.io.path.exists

/**
 * Main LumaGuilds command handler for administrative functions
 */
class LumaGuildsCommand : CommandExecutor, TabCompleter, KoinComponent {

    private val lang: LangService by inject()
    private val guildService: GuildService by inject()
    private val adminOverrideService: AdminOverrideService by inject()

    // Resolved lazily and nullable: GuildRolePermissionResolver is only registered when
    // claims are enabled. Touching it via `by inject()` would crash the override command
    // on claims-disabled servers.
    private val guildRolePermissionResolver: GuildRolePermissionResolver?
        get() = getKoin().getOrNull()
    private val progressionConfigService: net.lumalyte.lg.infrastructure.services.ProgressionConfigService by inject()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.command.this_command_can_only_be_used_by"))
            return true
        }

        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "reload" -> handleReload(sender)
            "progressionreload" -> handleProgressionReload(sender)
            "disband" -> handleDisband(sender, args)
            "migrate" -> handleMigrate(sender, args)
            "override" -> handleOverride(sender)
            "help" -> showHelp(sender)
            else -> {
                sender.sendMessage(lang.msg("admin.migrated.luma_guilds.command.unknown_subcommand", "args" to args[0]))
                showHelp(sender)
            }
        }

        return true
    }

    /**
     * Handle force disbanding a guild (for admin emergency use)
     */
    private fun handleDisband(sender: CommandSender, args: Array<out String>) {
        // Check permissions - only console or ops can disband guilds
        if (sender is Player && !sender.isOp) {
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handledisband.you_don_t_have_permission_to_disband"))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handledisband.usage_bellclaims_disband_guild_name"))
            return
        }

        // Check if this is a confirmation (last arg is "confirm")
        val isConfirmation = args.size > 2 && args[args.size - 1].equals("confirm", ignoreCase = true)

        // Extract guild name (excluding "confirm" if present)
        val guildName = if (isConfirmation) {
            args.slice(1 until args.size - 1).joinToString(" ")
        } else {
            args.drop(1).joinToString(" ")
        }

        val guild = net.lumalyte.lg.utils.GuildResolver.resolveGuildByName(guildName, guildService)

        if (guild == null) {
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handledisband.guild_not_found", "guild" to guildName))
            return
        }

        if (!isConfirmation) {
            // Show confirmation prompt
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handledisband.warning_you_are_about_to_force_disband", "guild" to guild.name))
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handledisband.this_will_remove_all_members_and_delete"))
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handledisband.run_the_command_again_within_10_seconds"))
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handledisband.bellclaims_disband_confirm", "guild" to guild.name))
            return
        }

        // Perform the disband using console/system UUID
        val systemUuid = java.util.UUID.fromString("00000000-0000-0000-0000-000000000000")
        val success = guildService.disbandGuild(guild.id, systemUuid)

        if (success) {
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handledisband.guild_has_been_forcefully_disbanded", "guild" to guild.name))
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handledisband.all_members_have_been_removed_from_the"))
        } else {
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handledisband.failed_to_disband_guild", "guild" to guild.name))
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handledisband.check_server_console_for_errors"))
        }
    }

    /**
     * Handle plugin reload (for development)
     */
    private fun handleReload(sender: CommandSender) {
        // Check permissions - only console or ops can reload
        if (sender is Player && !sender.isOp) {
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlereload.you_don_t_have_permission_to_reload"))
            return
        }

        try {
            // Get the plugin instance
            val plugin = sender.server.pluginManager.getPlugin("LumaGuilds") as? LumaGuilds
            if (plugin == null) {
                sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlereload.lumaguilds_plugin_not_found"))
                return
            }

            // Reload the configuration
            plugin.reloadConfig()
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlereload.reloading_lumaguilds_configuration"))

            // Reinitialize config and services
            plugin.initConfig()

            val emojiResult = org.koin.core.context.GlobalContext.get()
                .get<net.lumalyte.lg.infrastructure.services.GuildEmojiGrantService>()
                .reconcileAll()
            if (!emojiResult.successful) {
                sender.sendMessage("LumaGuilds emoji permissions reconciled with ${emojiResult.failed} failure(s); check console.")
            }

            // Refresh cached configs in listeners
            plugin.vaultProtectionListener.refreshConfig()
            org.koin.core.context.GlobalContext.get()
                .getOrNull<net.lumalyte.lg.infrastructure.listeners.ProgressionEventListener>()
                ?.refreshCaches()

            // Note: We don't reinitialize the entire plugin as that would require
            // stopping and restarting schedulers, recreating Koin context, etc.
            // For development, config reload should be sufficient.

            if (emojiResult.successful) {
                sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlereload.lumaguilds_configuration_reloaded_successfully"))
                sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlereload.some_changes_may_require_a_full_server"))
            }

        } catch (e: Exception) {
            // Command handler - catching all exceptions to prevent command crash
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlereload.failed_to_reload_plugin", "reason" to e.message))
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlereload.you_may_need_to_restart_the_server"))
        }
    }

    /**
     * Handle progression config reload
     */
    private fun handleProgressionReload(sender: CommandSender) {
        // Check permissions - only console or ops can reload
        if (sender is Player && !sender.isOp) {
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handleprogressionreload.you_don_t_have_permission_to_reload"))
            return
        }

        try {
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handleprogressionreload.reloading_progression_yml_configuration"))

            // Reload the progression configuration
            progressionConfigService.reloadProgressionConfig()
            org.koin.core.context.GlobalContext.get()
                .getOrNull<net.lumalyte.lg.infrastructure.listeners.ProgressionEventListener>()
                ?.refreshCaches()

            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handleprogressionreload.progression_configuration_reloaded_successfully"))
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handleprogressionreload.changes_to_level_rewards_and_xp_sources"))
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handleprogressionreload.existing_guild_levels_and_xp_are_unaffected"))

        } catch (e: Exception) {
            // Command handler - catching all exceptions to prevent command crash
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handleprogressionreload.failed_to_reload_progression_config", "reason" to e.message))
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handleprogressionreload.check_your_progression_yml_file_for_errors"))
        }
    }

    /**
     * Handle admin override toggle
     */
    private fun handleOverride(sender: CommandSender) {
        // Only players can use this command
        if (sender !is Player) {
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handleoverride.only_players_can_use_this_command"))
            return
        }

        // Check permissions
        if (!sender.hasPermission("bellclaims.admin")) {
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handleoverride.you_don_t_have_permission_to_use"))
            return
        }

        // Toggle the override state
        val newState = adminOverrideService.toggleOverride(sender.uniqueId)

        // Invalidate the claim-permission cache so changes apply immediately. Resolver is
        // null on claims-disabled servers; the override still toggles for guild-level checks.
        guildRolePermissionResolver?.invalidatePlayerCache(sender.uniqueId)

        // Send appropriate message based on new state
        if (newState) {
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handleoverride.admin_guild_override_enabled"))
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handleoverride.you_now_have_owner_permissions_in_all"))
        } else {
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handleoverride.admin_guild_override_disabled"))
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handleoverride.you_no_longer_have_owner_permissions_in"))
        }
    }

    /**
     * Handle database migration from SQLite to MariaDB
     */
    private fun handleMigrate(sender: CommandSender, args: Array<out String>) {
        // Check permissions - only console or ops can migrate
        if (sender is Player && !sender.isOp) {
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.you_don_t_have_permission_to_migrate"))
            return
        }

        // Check if this is a confirmation
        val isConfirmation = args.size > 1 && args[1].equals("confirm", ignoreCase = true)

        if (!isConfirmation) {
            // Show confirmation prompt
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.warning_database_migration_sqlite_mariadb"))
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.this_will_copy_all_data_from_sqlite"))
            sender.sendMessage(lang.msg("command.common.blank_line"))
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.prerequisites"))
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.1_mariadb_must_be_configured_in_config"))
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.2_mariadb_must_be_running_and_accessible"))
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.3_the_mariadb_database_schema_must_be"))
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.start_server_with_database_type_mariadb_first"))
            sender.sendMessage(lang.msg("command.common.blank_line"))
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.warning_this_will_delete_all_existing_data"))
            sender.sendMessage(lang.msg("command.common.blank_line"))
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.run_the_command_again_to_confirm"))
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.bellclaims_migrate_confirm"))
            return
        }

        // Get plugin instance
        val plugin = Bukkit.getPluginManager().getPlugin("LumaGuilds") as? LumaGuilds
        if (plugin == null) {
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlereload.lumaguilds_plugin_not_found"))
            return
        }

        // Get MariaDB configuration
        val config = plugin.config
        val host = config.getString("mariadb.host", "localhost") ?: "localhost"
        val port = config.getInt("mariadb.port", 3306)
        val database = config.getString("mariadb.database", "lumaguilds") ?: "lumaguilds"
        val username = config.getString("mariadb.username", "root") ?: "root"
        val password = config.getString("mariadb.password", "password") ?: "password"

        // Get SQLite file
        val sqliteFile = File(plugin.dataFolder, "lumaguilds.db")
        if (!sqliteFile.exists()) {
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.sqlite_database_not_found", "absolute_path" to sqliteFile.absolutePath))
            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.cannot_migrate_no_source_database"))
            return
        }

        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.starting_database_migration"))
        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.from_sqlite", "sqlite_file" to sqliteFile.name))
        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.to_mariadb", "host" to host, "port" to port, "database" to database))
        sender.sendMessage(lang.msg("command.common.blank_line"))
        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.do_not_stop_the_server_during_migration"))

        // Run migration asynchronously to avoid blocking
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val migrator = DatabaseMigrationUtility(
                    plugin = plugin,
                    sqliteFile = sqliteFile,
                    mariadbHost = host,
                    mariadbPort = port,
                    mariadbDatabase = database,
                    mariadbUsername = username,
                    mariadbPassword = password
                )

                val report = migrator.migrate()

                // Print report to console (synchronously)
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    report.printReport(plugin.logger)

                    if (report.success) {
                        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.migration_completed_successfully"))
                        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.migrated_tables_with_total_rows", "size" to report.migratedTables.size, "total_rows" to report.totalRows))
                        sender.sendMessage(lang.msg("command.common.blank_line"))
                        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.next_steps"))
                        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.1_verify_the_data_in_mariadb"))
                        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.2_update_config_yml_database_type_mariadb"))
                        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.3_restart_the_server"))
                        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.4_test_thoroughly_before_going_to_production"))
                        sender.sendMessage(lang.msg("command.common.blank_line"))
                        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.your_sqlite_database_is_still_intact_as"))
                    } else {
                        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.migration_failed"))
                        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.check_server_console_for_details"))
                        if (report.errors.isNotEmpty()) {
                            sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.errors"))
                            report.errors.forEach { error ->
                                sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.blank_line", "error" to error))
                            }
                        }
                    }
                })

            } catch (e: Exception) {
            // Command handler - catching all exceptions to prevent command crash
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    sender.sendMessage(lang.msg("admin.migrated.luma_guilds.handlemigrate.migration_failed_with_exception", "reason" to e.message))
                    plugin.logger.severe("Migration exception: ${e.message}")
                    e.printStackTrace()
                })
            }
        })
    }

    /**
     * Show help message
     */
    private fun showHelp(sender: CommandSender) {
        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.showhelp.lumaguilds_admin_commands"))
        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.showhelp.bellclaims_reload_reload_plugin_configuration_op_only"))
        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.showhelp.bellclaims_progressionreload_reload_progression_yml_op_only"))
        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.showhelp.bellclaims_disband_guild_confirm_force_disband_a"))
        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.showhelp.bellclaims_migrate_confirm_migrate_sqlite_mariadb_op"))
        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.showhelp.bellclaims_override_toggle_admin_override_mode_admin"))
        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.showhelp.bellclaims_help_show_this_help"))
        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.showhelp.reload_commands_are_for_development_some_changes"))
        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.showhelp.disband_is_for_emergency_use_only_removes"))
        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.showhelp.migrate_transfers_all_data_from_sqlite_to"))
        sender.sendMessage(lang.msg("admin.migrated.luma_guilds.showhelp.override_grants_owner_permissions_in_all_guilds"))
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String> {
        if (sender !is Player) return mutableListOf()

        return when (args.size) {
            1 -> mutableListOf(
                "reload", "progressionreload", "disband", "migrate", "override", "help"
            ).filter { it.startsWith(args[0]) }.toMutableList()
            2 -> when (args[0].lowercase()) {
                "disband" -> {
                    net.lumalyte.lg.utils.GuildResolver.suggestions(guildService)
                        .filter { it.contains(args[1], ignoreCase = true) }
                        .toMutableList()
                }
                "migrate" -> mutableListOf("confirm")
                else -> mutableListOf()
            }
            3 -> when (args[0].lowercase()) {
                "disband" -> mutableListOf("confirm")
                else -> mutableListOf()
            }
            else -> mutableListOf()
        }
    }
}
