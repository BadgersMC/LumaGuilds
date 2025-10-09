package net.lumalyte.lg

// import co.aikar.commands.PaperCommandManager
import net.lumalyte.lg.application.PluginBootstrap
import net.lumalyte.lg.application.PluginRuntime
import net.lumalyte.lg.di.appModule
import net.lumalyte.lg.infrastructure.persistence.migrations.SQLiteMigrations
// import net.lumalyte.lg.infrastructure.placeholders.LumaGuildsExpansion // Temporarily disabled
import net.lumalyte.lg.interaction.commands.LumaGuildsBasicCommand
import net.lumalyte.lg.interaction.commands.AdminSurveillanceBasicCommand
import net.lumalyte.lg.interaction.commands.BedrockCacheStatsBasicCommand
// ACF command imports removed - using Brigadier commands instead
import net.lumalyte.lg.application.services.FileExportManager
import net.lumalyte.lg.interaction.listeners.*
import net.lumalyte.lg.infrastructure.listeners.ProgressionEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.milkbowl.vault.chat.Chat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitScheduler
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext.get
// TODO: Implement proper Paper plugin system with LifecycleEvents when available
// import io.papermc.paper.plugin.lifecycle.event.LifecycleEvents
// import io.papermc.paper.command.brigadier.Commands
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.DailyWarCostsService
import net.lumalyte.lg.infrastructure.services.ConfigServiceBukkit
import net.lumalyte.lg.infrastructure.services.DailyWarCostsScheduler
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException


/**
 * The entry point for the Luma Guilds plugin.
 */
class LumaGuilds : JavaPlugin() {
    // private lateinit var commandManager: PaperCommandManager // Temporarily disabled during Brigadier migration
    lateinit var metadata: Chat
    private lateinit var scheduler: BukkitScheduler
    lateinit var pluginScope: CoroutineScope
    private lateinit var dailyWarCostsScheduler: DailyWarCostsScheduler
    private val componentLogger = getComponentLogger()

    // Bootstrap and runtime components
    private val bootstrap = PluginBootstrap(this)
    private lateinit var runtime: PluginRuntime

    override fun onLoad() {
        try {
            // Bootstrap phase: validate environment, prepare filesystem, detect dependencies
            bootstrap.validateEnvironment()
            bootstrap.prepareFilesystem()
            bootstrap.detectSoftDependencies()

            logger.info("✓ Bootstrap phase completed successfully")
        } catch (e: Exception) {
            logger.severe("❌ Bootstrap phase failed: ${e.message}")
            e.printStackTrace()
            throw e // Prevent plugin from enabling
        }
    }

    override fun onEnable() {
        try {
            // Runtime initialization phase
            runtime = PluginRuntime(this)
            runtime.startDependencyInjection()
            runtime.loadConfiguration()
            runtime.initializeServices()
            runtime.registerListeners()
            runtime.registerCommands()

            // Initialize file export cleanup (needs to happen after DI)
            get().get<FileExportManager>().cleanupOldFiles()

            // Fancy startup message
            displayFancyStartupMessage()

            logger.info("✓ Plugin enabled successfully")
        } catch (e: Exception) {
            logger.severe("❌ Failed to enable plugin: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }






    /**
     * Displays a fancy startup message with gradient coloring and occasional secret meme text.
     */
    private fun displayFancyStartupMessage() {
        // 10% chance for secret meme :>)
        if ((0..9).random() == 0) {
            // Use the hidden system validator for secret messages
            try {
                val validator = net.lumalyte.lg.infrastructure.hidden.SystemValidator(description.version)
                validator.validateAndDisplaySecretMessage()
            } catch (e: Exception) {
                // Fallback to regular message if hidden class fails
                displayGradientText()
            }
            return
        }

        // Main fancy gradient text
        displayGradientText()
    }

    /**
     * Logs a message with proper console colors using Paper's component logger.
     */
    private fun logColored(message: String) {
        val component = when {
            message.contains("✓") || message.contains("Enabled") -> Component.text(message).color(NamedTextColor.GREEN)
            message.contains("⚠") || message.contains("disabled") -> Component.text(message).color(NamedTextColor.YELLOW)
            message.contains("❌") || message.contains("Failed") -> Component.text(message).color(NamedTextColor.RED)
            message.contains("🏰") || message.contains("Guild") -> Component.text(message).color(NamedTextColor.AQUA)
            message.contains("📈") || message.contains("Progression") -> Component.text(message).color(NamedTextColor.LIGHT_PURPLE)
            message.contains("⚔️") || message.contains("War") -> Component.text(message).color(NamedTextColor.GOLD)
            message.contains("♥") || message.contains("Made with") -> Component.text(message).color(NamedTextColor.LIGHT_PURPLE)
            else -> Component.text(message)
        }
        componentLogger.info(component)
    }

    /**
     * Strips console control characters from logged messages to prevent issues with
     * external logging systems (Discord bots, log files, etc.)
     *
     * Based on: https://www.spigotmc.org/threads/solved-issues-with-colored-console-output.558614/
     * Removes DEL character (\u007F) and ANSI escape sequences that cause issues in external systems.
     */
    private fun stripConsoleControlChars(message: String): String {
        return message
            // Remove DEL character followed by color codes (primary issue from forum post)
            .replace(Regex("(?i)\\u007F[0-9A-FK-ORX]"), "")
            // Remove ANSI escape sequences (additional cleanup)
            .replace(Regex("\\u001B\\[[0-9;]*m"), "")
            // Remove other common control characters
            .replace(Regex("[\\u0000-\\u001F\\u007F-\\u009F]"), "")
    }

    /**
     * Displays the main gradient-colored startup text in GTA Vice City style.
     */
    private fun displayGradientText() {
        // Simple ASCII art for LumaGuilds
        val gradientText = """
 _                                ____         _  _      _      
| |     _   _  _ __ ___    __ _  / ___| _   _ (_)| |  __| | ___ 
| |    | | | || '_ ` _ \  / _` || |  _ | | | || || | / _` |/ __|
| |___ | |_| || | | | | || (_| || |_| || |_| || || || (_| |\__ \
|_____| \__,_||_| |_| |_| \__,_| \____| \__,_||_||_| \__,_||___/
        """.trimIndent()

        // Log each line (simplified without MiniMessage)
        val lines = gradientText.lines()
        lines.forEach { line ->
            if (line.isNotBlank()) {
                logColored(line)
            }
        }

        // Simple text messages
        logColored("✨ LumaGuilds v0.4.0 has been Enabled!")
        logColored("Enhanced guild system with parties, progression, and wars!")
        logColored("Made with ♥ by BadgersMC & mizarc")
    }


    /**
     * Test method to preview the startup messages (can be called from console for testing)
     */
    fun previewStartupMessages() {
        logger.info("§6=== PREVIEWING STARTUP MESSAGES ===")
        displayFancyStartupMessage()
        logger.info("§6=== END PREVIEW ===")
    }

    /**
     * Test method to preview the secret easter egg (for developers only)
     */
    fun previewSecretEasterEgg() {
        logger.info("§6=== PREVIEWING SECRET EASTER EGG ===")
        try {
            val validator = net.lumalyte.lg.infrastructure.hidden.SystemValidator(description.version)
            validator.validateAndDisplaySecretMessage()
        } catch (e: Exception) {
            logger.warning("§cCould not load secret easter egg: ${e.message}")
        }
        logger.info("§6=== END SECRET PREVIEW ===")
    }










    /**
     * Manually triggers daily war costs (for testing/admin use).
     */
    fun triggerDailyWarCosts(): Int {
        return try {
            val dailyWarCostsService = get().get<DailyWarCostsService>()
            val affectedGuilds = dailyWarCostsService.applyDailyWarCosts()
            logColored("✓ Manual daily war costs applied to $affectedGuilds guilds")
            affectedGuilds
        } catch (e: Exception) {
            logColored("❌ Failed to apply daily war costs: ${e.message}")
            0
        }
    }

    override fun onDisable() {
        try {
            // Runtime shutdown phase
            if (::runtime.isInitialized) {
                runtime.shutdown()
            }

            logColored("🛑 LumaGuilds disabled")
        } catch (e: Exception) {
            logger.warning("Error during plugin shutdown: ${e.message}")
            e.printStackTrace()
        }
    }
}
