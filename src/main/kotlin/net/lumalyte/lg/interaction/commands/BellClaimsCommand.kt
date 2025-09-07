package net.lumalyte.lg.interaction.commands

import net.lumalyte.lg.application.services.FileExportManager
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.nio.file.Files
import kotlin.io.path.exists

/**
 * Main BellClaims command handler for administrative functions
 */
class BellClaimsCommand : CommandExecutor, TabCompleter, KoinComponent {

    private val fileExportManager: FileExportManager by inject()

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

        val pluginDataFolder = player.server.pluginManager.getPlugin("BellClaims")?.dataFolder
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
     * Show help message
     */
    private fun showHelp(sender: CommandSender) {
        sender.sendMessage("§6=== BellClaims Admin Commands ===")
        sender.sendMessage("§e/bellclaims download <filename> §7- Download an exported CSV file")
        sender.sendMessage("§e/bellclaims exports §7- List your active exports")
        sender.sendMessage("§e/bellclaims cancel <filename> §7- Cancel an active export")
        sender.sendMessage("§e/bellclaims help §7- Show this help")
        sender.sendMessage("§7💡 Export files are available for 15 minutes")
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
        meta.author = "BellClaims"
        // Convert pages to Components for the book
        pages.forEach { page ->
            meta.addPages(net.kyori.adventure.text.Component.text(page))
        }

        // Add lore with file info (using modern Adventure API)
        val fileSizeKB = content.toByteArray().size / 1024.0
        meta.lore(listOf(
            net.kyori.adventure.text.Component.text("§7File: $fileName"),
            net.kyori.adventure.text.Component.text("§7Size: ${String.format("%.1f", fileSizeKB)} KB"),
            net.kyori.adventure.text.Component.text("§7Exported from BellClaims")
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
            1 -> mutableListOf("download", "exports", "cancel", "help").filter { it.startsWith(args[0]) }.toMutableList()
            2 -> when (args[0].lowercase()) {
                "download", "cancel" -> {
                    fileExportManager.getActiveExports(sender.uniqueId)
                        .filter { it.startsWith(args[1]) }
                        .toMutableList()
                }
                else -> mutableListOf()
            }
            else -> mutableListOf()
        }
    }
}
