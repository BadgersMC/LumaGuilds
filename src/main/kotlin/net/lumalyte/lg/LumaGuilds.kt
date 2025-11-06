package net.lumalyte.lg

import co.aikar.commands.PaperCommandManager
import net.lumalyte.lg.di.appModule
import net.lumalyte.lg.infrastructure.persistence.migrations.SQLiteMigrations
import net.lumalyte.lg.infrastructure.placeholders.LumaGuildsExpansion
import net.lumalyte.lg.interaction.commands.*
import net.lumalyte.lg.interaction.commands.LumaGuildsCommand
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
    private lateinit var commandManager: PaperCommandManager
    lateinit var metadata: Chat
    private lateinit var scheduler: BukkitScheduler
    lateinit var pluginScope: CoroutineScope
    private lateinit var dailyWarCostsScheduler: DailyWarCostsScheduler
    private val componentLogger = getComponentLogger()


    override fun onEnable() {
        initConfig()

        // Check if claims are enabled BEFORE initializing database
        val claimsEnabled = config.getBoolean("claims_enabled", true)

        initDatabase(claimsEnabled)
        pluginScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scheduler = server.scheduler
        initLang()
        initialiseVaultDependency()
        initialisePlaceholderAPI()
        initialiseAxKothIntegration()
        commandManager = PaperCommandManager(this)

        // Enable case-insensitive command completion and parsing
        commandManager.enableUnstableAPI("help")

        // Start Koin with claims enabled/disabled
        startKoin { modules(appModule(this@LumaGuilds, claimsEnabled)) }

        // Configure command completions (ACF handles this automatically)
        configureCommandCompletions()

        // Log configuration summary now that Koin is initialized
        logConfigurationSummary()

        if (claimsEnabled) {
            registerClaimCommands()
            registerClaimEvents()
            logColored("✓ Claims system enabled")
        } else {
            logColored("⚠ Claims system disabled - all claim features unavailable")
        }

        registerNonClaimCommands()
        registerNonClaimEvents()

        // Initialize file export cleanup
        get().get<FileExportManager>().cleanupOldFiles()

        // Initialize daily war costs scheduler
        initDailyWarCostsScheduler()

        // Fancy startup message
        displayFancyStartupMessage()
    }

    fun initConfig() {
        // Save default config if it doesn't exist
        saveDefaultConfig()
        reloadConfig()
    }

    private fun logConfigurationSummary() {
        val configService = ConfigServiceBukkit(this.config)
        val config = configService.loadConfig()

        // Basic config info
        logColored("📋 Configuration loaded: ${this.config.getKeys(false).size} top-level keys")

        // Claims system status
        val claimsStatus = if (config.claimsEnabled) {
            "✓ Claims system enabled"
        } else {
            "⚠ Claims system disabled"
        }
        logColored(claimsStatus)

        // Guild system info
        logColored("🏰 Guild system: Max ${config.guild.maxGuildCount} guilds, Max ${config.guild.maxMembersPerGuild} members/guild")

        // Progression system info
        logColored("📈 Progression: XP rate ${config.progression.playerKillXp} (player kills), ${config.progression.cropBreakXp} (crops)")

        // Party system info
        val partyStatus = if (config.party.allowPrivateParties) {
            "✓ Private parties allowed"
        } else {
            "✗ Private parties disabled"
        }
        logColored(partyStatus)

        // War system info
        logColored("⚔️ War system: Kill-based objectives, High-stakes wagering")
    }

    fun initDatabase(claimsEnabled: Boolean) {
        // Ensure the plugin data folder exists
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
            logger.info("Created plugin data folder: ${dataFolder.absolutePath}")
        }
        
        val databaseFile = File(dataFolder, "claims.db")
        if (databaseFile.exists()) {
            logColored("💾 Existing database found. Claims enabled: $claimsEnabled")

            var tempConnectionForMigration: Connection? = null
            try {
                tempConnectionForMigration = DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}")

                if (claimsEnabled) {
                    // Run migrations only if claims are enabled
                    val migrator = SQLiteMigrations(this, tempConnectionForMigration)
                    migrator.migrate()
                } else {
                    logColored("⏭️ Claims system disabled - skipping migrations but preserving existing guild data")
                }
            } finally {
                tempConnectionForMigration?.let {
                    try {
                        if (!it.isClosed) {
                            it.close()
                            logger.info("Closed temporary connection after database check.")
                        }
                    } catch (e: SQLException) {
                        logColored("❌ Failed to close temporary database connection: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        } else {
            // No existing database
            if (claimsEnabled) {
                logColored("🆕 Database file not found. Creating a new database with full schema...")

                var newConnection: Connection? = null
                try {
                    // This will create the database file if it doesn't exist
                    newConnection = DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}")
                    val migrator = SQLiteMigrations(this, newConnection)
                    migrator.migrate()
                } catch (e: SQLException) {
                    logColored("❌ Failed to create new database or run migrations: ${e.message}")
                    e.printStackTrace()
                } finally {
                    newConnection?.let {
                        try {
                            if (!it.isClosed) {
                                it.close()
                                logger.info("Closed connection for new database creation.")
                            }
                        } catch (e: SQLException) {
                            logColored("❌ Failed to close new database connection: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                }
            } else {
                logger.info("Claims system disabled and no existing database - skipping database creation")
            }
        }
    }

    fun initLang() {
        val defaultLanguageFilenames = listOf(
            "en.properties"
        )

        // Move languages to the required folder and add readme for override instructions
        defaultLanguageFilenames.forEach { filename ->
            val resourcePathInJar = "lang/defaults/$filename"
            saveResource(resourcePathInJar, true)
        }
        saveResource("lang/overrides/README.txt", true)
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


    private fun initialiseVaultDependency() {
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            server.servicesManager.getRegistration(Chat::class.java)?.let { metadata = it.provider }
            logger.info(Chat::class.java.toString())
        }
    }

    /**
     * Registers the PlaceholderAPI expansion if PlaceholderAPI is available.
     */
    private fun initialisePlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                val expansion = LumaGuildsExpansion()
                if (expansion.register()) {
                    logColored("✓ Successfully registered LumaGuilds PlaceholderAPI expansion!")
                    logColored("Available placeholders: %lumaguilds_guild_name%, %lumaguilds_guild_tag%, etc.")
                } else {
                    logColored("❌ Failed to register LumaGuilds PlaceholderAPI expansion!")
                }
            } catch (e: Exception) {
                logger.severe("Error registering PlaceholderAPI expansion: ${e.message}")
                e.printStackTrace()
            }
        } else {
            logColored("⚠ PlaceholderAPI not found. LumaGuilds placeholders will not be available.")
        }
    }

    /**
     * Registers the AxKoth team hook if AxKoth is available.
     * This allows AxKoth to recognize guilds as teams for KOTH events.
     */
    private fun initialiseAxKothIntegration() {
        if (Bukkit.getPluginManager().getPlugin("AxKoth") != null) {
            try {
                val hook = net.lumalyte.lg.integrations.axkoth.LumaGuildsHook()
                com.artillexstudios.axkoth.api.AxKothAPI.registerTeamHook(this, hook)
                logColored("✓ Successfully registered LumaGuilds hook with AxKoth!")
                logColored("Guilds can now compete in KOTH events as teams")
            } catch (e: Exception) {
                logger.severe("Error registering AxKoth integration: ${e.message}")
                e.printStackTrace()
            }
        } else {
            logColored("⚠ AxKoth not found. Guild KOTH integration unavailable.")
        }
    }

    /**
     * Configures basic command completions using ACF's built-in system.
     * ACF automatically handles tab completion through @CommandCompletion annotations
     * on command methods, providing case-insensitive completion out of the box.
     */
    private fun configureCommandCompletions() {
        // ACF handles tab completion automatically through @CommandCompletion annotations
        // No custom setup needed - the framework provides robust completion features
    }

    /**
     * Registers claim-related commands.
     */
    private fun registerClaimCommands() {
        commandManager.registerCommand(ClaimListCommand())
        commandManager.registerCommand(ClaimCommand())
        commandManager.registerCommand(InfoCommand())
        commandManager.registerCommand(RenameCommand())
        commandManager.registerCommand(DescriptionCommand())
        commandManager.registerCommand(PartitionsCommand())
        commandManager.registerCommand(AddFlagCommand())
        commandManager.registerCommand(RemoveFlagCommand())
        commandManager.registerCommand(TrustListCommand())
        commandManager.registerCommand(TrustCommand())
        commandManager.registerCommand(TrustAllCommand())
        commandManager.registerCommand(UntrustCommand())
        commandManager.registerCommand(UntrustAllCommand())
        commandManager.registerCommand(RemoveCommand())
        commandManager.registerCommand(ClaimOverrideCommand())
        commandManager.registerCommand(ClaimMenuCommand())
    }

    /**
     * Registers non-claim commands (guilds, etc.).
     */
    private fun registerNonClaimCommands() {
        commandManager.registerCommand(GuildCommand())
        commandManager.registerCommand(PartyChatCommand())

        // Register LumaGuilds admin command
        getCommand("lumaguilds")?.setExecutor(LumaGuildsCommand())
        getCommand("lumaguilds")?.tabCompleter = LumaGuildsCommand()

        // Register Bedrock cache stats command
        getCommand("bedrockcachestats")?.setExecutor(BedrockCacheStatsCommand())

        logColored("✓ Admin commands registered (/lumaguilds, /bellclaims)")
        logColored("✓ Bedrock cache stats command registered (/bedrockcachestats)")
    }

    /**
     * Registers claim-related listeners.
     */
    private fun registerClaimEvents() {
        server.pluginManager.registerEvents(BlockLaunchListener(this), this)
        server.pluginManager.registerEvents(ClaimAnchorListener(), this)
        server.pluginManager.registerEvents(ClaimDestructionListener(), this)
        server.pluginManager.registerEvents(EditToolListener(), this)
        server.pluginManager.registerEvents(EditToolVisualisingListener(this), this)
        server.pluginManager.registerEvents(HarvestReplantListener(), this)
        server.pluginManager.registerEvents(MoveToolListener(), this)
        server.pluginManager.registerEvents(PartitionUpdateListener(), this)
        server.pluginManager.registerEvents(PlayerClaimProtectionListener(), this)
        server.pluginManager.registerEvents(ToolRemovalListener(), this)
        server.pluginManager.registerEvents(WorldClaimProtectionListener(), this)
        server.pluginManager.registerEvents(CloseInventoryListener(), this)
    }

    /**
     * Registers non-claim listeners.
     */
    private fun registerNonClaimEvents() {
        // Use the DI instance of ChatInputListener to avoid duplicate instances
        val chatInputListener = get().get<ChatInputListener>()
        server.pluginManager.registerEvents(chatInputListener, this)
        server.pluginManager.registerEvents(BannerSelectionListener(), this)

        // Register progression event listener
        val progressionEventListener = get().get<ProgressionEventListener>()
        server.pluginManager.registerEvents(progressionEventListener, this)

        // Register vault protection listener
        server.pluginManager.registerEvents(net.lumalyte.lg.infrastructure.listeners.VaultProtectionListener(), this)

        // Register player session cleanup listener
        server.pluginManager.registerEvents(net.lumalyte.lg.infrastructure.listeners.PlayerSessionListener(), this)
    }

    /**
     * Initializes the daily war costs scheduler.
     */
    private fun initDailyWarCostsScheduler() {
        try {
            val dailyWarCostsService = get().get<DailyWarCostsService>()
            dailyWarCostsScheduler = DailyWarCostsScheduler(this, dailyWarCostsService)
            dailyWarCostsScheduler.startDailyScheduler()
            logColored("✓ Daily war costs scheduler started")
        } catch (e: Exception) {
            logColored("❌ Failed to initialize daily war costs scheduler: ${e.message}")
        }
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
        // Stop the daily war costs scheduler
        if (::dailyWarCostsScheduler.isInitialized) {
            dailyWarCostsScheduler.stopDailyScheduler()
        }

        // Cancel any remaining plugin scope tasks
        pluginScope.cancel()

        logColored("🛑 LumaGuilds disabled")
    }
}
