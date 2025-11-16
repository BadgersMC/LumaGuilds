package net.lumalyte.lg.interaction.commands

import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.services.FileExportManager
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
import java.nio.file.Files
import kotlin.io.path.exists

/**
 * Main LumaGuilds command handler for administrative functions
 */
class LumaGuildsCommand : CommandExecutor, TabCompleter, KoinComponent {

    private val fileExportManager: FileExportManager by inject()
    private val guildService: GuildService by inject()

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
            "download" -> handleDownload(sender, args)
            "exports" -> handleListExports(sender)
            "cancel" -> handleCancelExport(sender, args)
            "reload" -> handleReload(sender)
            "disband" -> handleDisband(sender, args)
            "migrate" -> handleMigrate(sender, args)
            "help" -> showHelp(sender)
            else -> {
                sender.sendMessage("§cUnknown subcommand: ${args[0]}")
                showHelp(sender)
            }
        }

        return true
    }

    /**
     * Handle file download
     */
    private fun handleDownload(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage("§cUsage: /bellclaims download <filename>")
            return
        }

        val fileName = args[1]

        // Security: Validate filename to prevent path traversal
        if (!isValidFileName(fileName)) {
            player.sendMessage("§c❌ Invalid filename!")
            return
        }

        val pluginDataFolder = player.server.pluginManager.getPlugin("LumaGuilds")?.dataFolder
            ?: return player.sendMessage("§c❌ Plugin error!")

        val tempDir = pluginDataFolder.resolve("temp_exports")
        val filePath = tempDir.toPath().resolve(fileName)

        // Check if file exists and is accessible
        if (!filePath.exists()) {
            player.sendMessage("§c❌ File not found or expired: $fileName")
            player.sendMessage("§7Files are automatically deleted after 15 minutes for security.")
            return
        }

        try {
            // Read file content
            val fileContent = Files.readString(filePath)

            // Create a book with the CSV content
            val book = createBookWithContent(fileName, fileContent)

            // Give the book to player
            val remaining = player.inventory.addItem(book)
            if (remaining.isNotEmpty()) {
                // Inventory full, drop at feet
                player.world.dropItem(player.location, book)
                player.sendMessage("§e📖 Book dropped at your feet (inventory full)")
            }

            player.sendMessage("§a✅ Downloaded: $fileName")
            player.sendMessage("§7💡 The file has been converted to a book for easy reading")

            // Delete the file after successful download
            Files.deleteIfExists(filePath)

        } catch (e: Exception) {
            player.sendMessage("§c❌ Failed to download file: ${e.message}")
        }
    }

    /**
     * Handle listing active exports
     */
    private fun handleListExports(player: Player) {
        val activeExports = fileExportManager.getActiveExports(player.uniqueId)

        if (activeExports.isEmpty()) {
            player.sendMessage("§7📄 No active exports")
            return
        }

        player.sendMessage("§e📄 Your active exports:")
        activeExports.forEach { fileName ->
            player.sendMessage("§7  • $fileName")
        }
        player.sendMessage("§7💡 Use §6/bellclaims download <filename> §7to get a file")
    }

    /**
     * Handle canceling an active export
     */
    private fun handleCancelExport(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage("§cUsage: /bellclaims cancel <filename>")
            return
        }

        val fileName = args[1]
        val success = fileExportManager.cancelExport(player.uniqueId, fileName)

        if (success) {
            player.sendMessage("§a✅ Canceled export: $fileName")
        } else {
            player.sendMessage("§c❌ Could not cancel export: $fileName")
        }
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

        val guild = guildService.getGuildByName(guildName)

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

            // Note: We don't reinitialize the entire plugin as that would require
            // stopping and restarting schedulers, recreating Koin context, etc.
            // For development, config reload should be sufficient.

            sender.sendMessage("§a✅ LumaGuilds configuration reloaded successfully!")
            sender.sendMessage("§7💡 Some changes may require a full server restart to take effect.")

        } catch (e: Exception) {
            sender.sendMessage("§c❌ Failed to reload plugin: ${e.message}")
            sender.sendMessage("§7💡 You may need to restart the server for changes to take effect.")
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
        sender.sendMessage("§e/bellclaims download <filename> §7- Download an exported CSV file")
        sender.sendMessage("§e/bellclaims exports §7- List your active exports")
        sender.sendMessage("§e/bellclaims cancel <filename> §7- Cancel an active export")
        sender.sendMessage("§e/bellclaims reload §7- Reload plugin configuration (OP only)")
        sender.sendMessage("§e/bellclaims disband <guild> confirm §7- Force disband a guild (OP only)")
        sender.sendMessage("§e/bellclaims migrate confirm §7- Migrate SQLite → MariaDB (OP only)")
        sender.sendMessage("§e/bellclaims help §7- Show this help")
        sender.sendMessage("§7💡 Export files are available for 15 minutes")
        sender.sendMessage("§7🔧 Reload command is for development - some changes require server restart")
        sender.sendMessage("§7⚠️ Disband is for emergency use only - removes all members!")
        sender.sendMessage("§7🔄 Migrate transfers all data from SQLite to MariaDB (requires confirmation)")
    }

    /**
     * Validate filename to prevent security issues
     */
    private fun isValidFileName(fileName: String): Boolean {
        // Allow only alphanumeric characters, underscores, hyphens, and dots
        // Maximum length of 100 characters
        // Must contain at least one dot (for file extension)
        return fileName.matches(Regex("^[a-zA-Z0-9_\\-.]{1,100}$")) &&
               fileName.contains(".") &&
               !fileName.contains("..") && // Prevent directory traversal
               !fileName.startsWith(".") && // Prevent hidden files
               fileName.endsWith(".csv") // Only allow CSV files
    }

    /**
     * Create a written book with CSV content
     */
    private fun createBookWithContent(fileName: String, content: String): org.bukkit.inventory.ItemStack {
        val book = org.bukkit.inventory.ItemStack(org.bukkit.Material.WRITTEN_BOOK)
        val meta = book.itemMeta as? org.bukkit.inventory.meta.BookMeta ?: return book

        // Split content into pages (Minecraft book limit is about 255 chars per page)
        val pages = splitIntoPages(content, 240) // Leave some margin

        meta.title = "CSV Export"
        meta.author = "LumaGuilds"
        // Convert pages to Components for the book
        pages.forEach { page ->
            meta.addPages(net.kyori.adventure.text.Component.text(page))
        }

        // Add lore with file info (using modern Adventure API)
        val fileSizeKB = content.toByteArray().size / 1024.0
        meta.lore(listOf(
            net.kyori.adventure.text.Component.text("§7File: $fileName"),
            net.kyori.adventure.text.Component.text("§7Size: ${String.format("%.1f", fileSizeKB)} KB"),
            net.kyori.adventure.text.Component.text("§7Exported from LumaGuilds")
        ))

        book.itemMeta = meta
        return book
    }

    /**
     * Split content into book pages
     */
    private fun splitIntoPages(content: String, maxCharsPerPage: Int): List<String> {
        val pages = mutableListOf<String>()
        var remainingContent = content

        while (remainingContent.isNotEmpty()) {
            if (remainingContent.length <= maxCharsPerPage) {
                pages.add(remainingContent)
                break
            }

            // Find a good break point (try to break at line endings)
            var breakPoint = maxCharsPerPage
            for (i in (maxCharsPerPage - 50)..maxCharsPerPage) {
                if (remainingContent[i] == '\n') {
                    breakPoint = i
                    break
                }
            }

            val page = remainingContent.substring(0, breakPoint)
            pages.add(page)
            remainingContent = remainingContent.substring(breakPoint).trimStart()
        }

        return pages
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String> {
        if (sender !is Player) return mutableListOf()

        return when (args.size) {
            1 -> mutableListOf("download", "exports", "cancel", "reload", "disband", "migrate", "help").filter { it.startsWith(args[0]) }.toMutableList()
            2 -> when (args[0].lowercase()) {
                "download", "cancel" -> {
                    fileExportManager.getActiveExports(sender.uniqueId)
                        .filter { it.startsWith(args[1]) }
                        .toMutableList()
                }
                "disband" -> {
                    // Tab complete guild names
                    guildService.getAllGuilds()
                        .map { it.name }
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
