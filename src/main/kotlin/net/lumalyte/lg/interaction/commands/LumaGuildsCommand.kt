package net.lumalyte.lg.interaction.commands

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
            sender.sendMessage("§cThis command can only be used by players!")
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
                sender.sendMessage("§cUnknown subcommand: ${args[0]}")
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
            sender.sendMessage("§c❌ You don't have permission to disband guilds!")
            return
        }

        if (args.size < 2) {
            sender.sendMessage("§cUsage: /bellclaims disband <guild_name>")
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
            sender.sendMessage("§c❌ Guild not found: $guildName")
            return
        }

        if (!isConfirmation) {
            // Show confirmation prompt
            sender.sendMessage("§e⚠️ WARNING: You are about to force-disband the guild: §6${guild.name}")
            sender.sendMessage("§7This will remove all members and delete the guild permanently!")
            sender.sendMessage("§7Run the command again within 10 seconds to confirm:")
            sender.sendMessage("§e/bellclaims disband ${guild.name} confirm")
            return
        }

        // Perform the disband using console/system UUID
        val systemUuid = java.util.UUID.fromString("00000000-0000-0000-0000-000000000000")
        val success = guildService.disbandGuild(guild.id, systemUuid)

        if (success) {
            sender.sendMessage("§a✅ Guild '${guild.name}' has been forcefully disbanded!")
            sender.sendMessage("§7All members have been removed from the guild.")
        } else {
            sender.sendMessage("§c❌ Failed to disband guild '${guild.name}'")
            sender.sendMessage("§7Check server console for errors.")
        }
    }

    /**
     * Handle plugin reload (for development)
     */
    private fun handleReload(sender: CommandSender) {
        // Check permissions - only console or ops can reload
        if (sender is Player && !sender.isOp) {
            sender.sendMessage("§c❌ You don't have permission to reload the plugin!")
            return
        }

        try {
            // Get the plugin instance
            val plugin = sender.server.pluginManager.getPlugin("LumaGuilds") as? LumaGuilds
            if (plugin == null) {
                sender.sendMessage("§c❌ LumaGuilds plugin not found!")
                return
            }

            // Reload the configuration
            plugin.reloadConfig()
            sender.sendMessage("§e🔄 Reloading LumaGuilds configuration...")

            // Reinitialize config and services
            plugin.initConfig()

            // Refresh cached configs in listeners
            plugin.vaultProtectionListener.refreshConfig()
            org.koin.core.context.GlobalContext.get()
                .getOrNull<net.lumalyte.lg.infrastructure.listeners.ProgressionEventListener>()
                ?.refreshCaches()

            // Note: We don't reinitialize the entire plugin as that would require
            // stopping and restarting schedulers, recreating Koin context, etc.
            // For development, config reload should be sufficient.

            sender.sendMessage("§a✅ LumaGuilds configuration reloaded successfully!")
            sender.sendMessage("§7💡 Some changes may require a full server restart to take effect.")

        } catch (e: Exception) {
            // Command handler - catching all exceptions to prevent command crash
            sender.sendMessage("§c❌ Failed to reload plugin: ${e.message}")
            sender.sendMessage("§7💡 You may need to restart the server for changes to take effect.")
        }
    }

    /**
     * Handle progression config reload
     */
    private fun handleProgressionReload(sender: CommandSender) {
        // Check permissions - only console or ops can reload
        if (sender is Player && !sender.isOp) {
            sender.sendMessage("§c❌ You don't have permission to reload progression config!")
            return
        }

        try {
            sender.sendMessage("§e🔄 Reloading progression.yml configuration...")

            // Reload the progression configuration
            progressionConfigService.reloadProgressionConfig()
            org.koin.core.context.GlobalContext.get()
                .getOrNull<net.lumalyte.lg.infrastructure.listeners.ProgressionEventListener>()
                ?.refreshCaches()

            sender.sendMessage("§a✅ Progression configuration reloaded successfully!")
            sender.sendMessage("§7💡 Changes to level rewards and XP sources are now active.")
            sender.sendMessage("§7    Existing guild levels and XP are unaffected.")

        } catch (e: Exception) {
            // Command handler - catching all exceptions to prevent command crash
            sender.sendMessage("§c❌ Failed to reload progression config: ${e.message}")
            sender.sendMessage("§7💡 Check your progression.yml file for errors.")
        }
    }

    /**
     * Handle admin override toggle
     */
    private fun handleOverride(sender: CommandSender) {
        // Only players can use this command
        if (sender !is Player) {
            sender.sendMessage("§c❌ Only players can use this command!")
            return
        }

        // Check permissions
        if (!sender.hasPermission("bellclaims.admin")) {
            sender.sendMessage("§c❌ You don't have permission to use this command!")
            return
        }

        // Toggle the override state
        val newState = adminOverrideService.toggleOverride(sender.uniqueId)

        // Invalidate the claim-permission cache so changes apply immediately. Resolver is
        // null on claims-disabled servers; the override still toggles for guild-level checks.
        guildRolePermissionResolver?.invalidatePlayerCache(sender.uniqueId)

        // Send appropriate message based on new state
        if (newState) {
            sender.sendMessage("§a✅ Admin guild override enabled!")
            sender.sendMessage("§7You now have owner permissions in all guilds.")
        } else {
            sender.sendMessage("§c❌ Admin guild override disabled!")
            sender.sendMessage("§7You no longer have owner permissions in all guilds.")
        }
    }

    /**
     * Handle database migration from SQLite to MariaDB
     */
    private fun handleMigrate(sender: CommandSender, args: Array<out String>) {
        // Check permissions - only console or ops can migrate
        if (sender is Player && !sender.isOp) {
            sender.sendMessage("§c❌ You don't have permission to migrate databases!")
            return
        }

        // Check if this is a confirmation
        val isConfirmation = args.size > 1 && args[1].equals("confirm", ignoreCase = true)

        if (!isConfirmation) {
            // Show confirmation prompt
            sender.sendMessage("§e⚠️ WARNING: Database Migration (SQLite → MariaDB)")
            sender.sendMessage("§7This will copy all data from SQLite to MariaDB.")
            sender.sendMessage("§7")
            sender.sendMessage("§7Prerequisites:")
            sender.sendMessage("§7  1. MariaDB must be configured in config.yml")
            sender.sendMessage("§7  2. MariaDB must be running and accessible")
            sender.sendMessage("§7  3. The MariaDB database schema must be initialized")
            sender.sendMessage("§7     (Start server with database_type: mariadb first)")
            sender.sendMessage("§7")
            sender.sendMessage("§c⚠️ WARNING: This will DELETE all existing data in MariaDB!")
            sender.sendMessage("§7")
            sender.sendMessage("§7Run the command again to confirm:")
            sender.sendMessage("§e/bellclaims migrate confirm")
            return
        }

        // Get plugin instance
        val plugin = Bukkit.getPluginManager().getPlugin("LumaGuilds") as? LumaGuilds
        if (plugin == null) {
            sender.sendMessage("§c❌ LumaGuilds plugin not found!")
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
            sender.sendMessage("§c❌ SQLite database not found: ${sqliteFile.absolutePath}")
            sender.sendMessage("§7Cannot migrate - no source database!")
            return
        }

        sender.sendMessage("§e🔄 Starting database migration...")
        sender.sendMessage("§7From: SQLite (${sqliteFile.name})")
        sender.sendMessage("§7To: MariaDB ($host:$port/$database)")
        sender.sendMessage("§7")
        sender.sendMessage("§c⚠️ DO NOT stop the server during migration!")

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
                        sender.sendMessage("§a✅ Migration completed successfully!")
                        sender.sendMessage("§7Migrated ${report.migratedTables.size} tables with ${report.totalRows} total rows")
                        sender.sendMessage("§7")
                        sender.sendMessage("§6📝 Next steps:")
                        sender.sendMessage("§7  1. Verify the data in MariaDB")
                        sender.sendMessage("§7  2. Update config.yml: database_type: mariadb")
                        sender.sendMessage("§7  3. Restart the server")
                        sender.sendMessage("§7  4. Test thoroughly before going to production")
                        sender.sendMessage("§7")
                        sender.sendMessage("§e💾 Your SQLite database is still intact as a backup!")
                    } else {
                        sender.sendMessage("§c❌ Migration failed!")
                        sender.sendMessage("§7Check server console for details")
                        if (report.errors.isNotEmpty()) {
                            sender.sendMessage("§7Errors:")
                            report.errors.forEach { error ->
                                sender.sendMessage("§c  - $error")
                            }
                        }
                    }
                })

            } catch (e: Exception) {
            // Command handler - catching all exceptions to prevent command crash
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    sender.sendMessage("§c❌ Migration failed with exception: ${e.message}")
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
        sender.sendMessage("§6=== LumaGuilds Admin Commands ===")
        sender.sendMessage("§e/bellclaims reload §7- Reload plugin configuration (OP only)")
        sender.sendMessage("§e/bellclaims progressionreload §7- Reload progression.yml (OP only)")
        sender.sendMessage("§e/bellclaims disband <guild> confirm §7- Force disband a guild (OP only)")
        sender.sendMessage("§e/bellclaims migrate confirm §7- Migrate SQLite → MariaDB (OP only)")
        sender.sendMessage("§e/bellclaims override §7- Toggle admin override mode (Admin only)")
        sender.sendMessage("§e/bellclaims help §7- Show this help")
        sender.sendMessage("§7🔧 Reload commands are for development - some changes require server restart")
        sender.sendMessage("§7⚠️ Disband is for emergency use only - removes all members!")
        sender.sendMessage("§7🔄 Migrate transfers all data from SQLite to MariaDB (requires confirmation)")
        sender.sendMessage("§7🔓 Override grants owner permissions in all guilds temporarily")
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
