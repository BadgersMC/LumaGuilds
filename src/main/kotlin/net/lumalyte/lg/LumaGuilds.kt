package net.lumalyte.lg

import co.aikar.commands.PaperCommandManager
import net.lumalyte.lg.di.appModule
import net.lumalyte.lg.infrastructure.persistence.migrations.SQLiteMigrations
import net.lumalyte.lg.infrastructure.placeholders.BellClaimsExpansion
import net.lumalyte.lg.interaction.commands.*
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
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitScheduler
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext.get
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.infrastructure.services.ConfigServiceBukkit
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
    private val miniMessage = MiniMessage.miniMessage()

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
        commandManager = PaperCommandManager(this)

        // Start Koin with claims enabled/disabled
        startKoin { modules(appModule(this@LumaGuilds, claimsEnabled)) }

        // Log configuration summary now that Koin is initialized
        logConfigurationSummary()

        if (claimsEnabled) {
            registerClaimCommands()
            registerClaimEvents()
            val claimsMsg = miniMessage.deserialize("<green><bold>✓</bold></green> <white>Claims system <green>enabled</white>")
            logger.info(PlainTextComponentSerializer.plainText().serialize(claimsMsg))
        } else {
            val claimsDisabledMsg = miniMessage.deserialize("<yellow><bold>⚠</bold></yellow> <white>Claims system <yellow>disabled <white>- all claim features unavailable</white>")
            logger.info(PlainTextComponentSerializer.plainText().serialize(claimsDisabledMsg))
        }

        registerNonClaimCommands()
        registerNonClaimEvents()

        // Initialize file export cleanup
        get().get<FileExportManager>().cleanupOldFiles()

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
        val basicInfoMsg = miniMessage.deserialize("<gray>📋 <white>Configuration loaded: ${this.config.getKeys(false).size} top-level keys</white>")
        logger.info(PlainTextComponentSerializer.plainText().serialize(basicInfoMsg))

        // Claims system status
        val claimsStatus = if (config.claimsEnabled) {
            "<green><bold>✓</bold></green> <white>Claims system <green>enabled</green></white>"
        } else {
            "<yellow><bold>⚠</bold></yellow> <white>Claims system <yellow>disabled</yellow></white>"
        }
        val claimsMsg = miniMessage.deserialize(claimsStatus)
        logger.info(PlainTextComponentSerializer.plainText().serialize(claimsMsg))

        // Guild system info
        val guildInfoMsg = miniMessage.deserialize("<blue><bold>🏰</bold></blue> <white>Guild system: Max ${config.guild.maxGuildCount} guilds, Max ${config.guild.maxMembersPerGuild} members/guild</white>")
        logger.info(PlainTextComponentSerializer.plainText().serialize(guildInfoMsg))

        // Progression system info
        val progressionInfoMsg = miniMessage.deserialize("<gold><bold>📈</bold></gold> <white>Progression: XP rate ${config.progression.playerKillXp} (player kills), ${config.progression.cropBreakXp} (crops)</white>")
        logger.info(PlainTextComponentSerializer.plainText().serialize(progressionInfoMsg))

        // Party system info
        val partyStatus = if (config.party.allowPrivateParties) {
            "<green><bold>✓</bold></green> <white>Private parties <green>allowed</green></white>"
        } else {
            "<gray><bold>✗</bold></gray> <white>Private parties <gray>disabled</gray></white>"
        }
        val partyMsg = miniMessage.deserialize(partyStatus)
        logger.info(PlainTextComponentSerializer.plainText().serialize(partyMsg))

        // War system info
        val warInfoMsg = miniMessage.deserialize("<red><bold>⚔️</bold></red> <white>War system: Kill-based objectives, High-stakes wagering</white>")
        logger.info(PlainTextComponentSerializer.plainText().serialize(warInfoMsg))
    }

    fun initDatabase(claimsEnabled: Boolean) {
        // Ensure the plugin data folder exists
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
            logger.info("Created plugin data folder: ${dataFolder.absolutePath}")
        }
        
        val databaseFile = File(dataFolder, "claims.db")
        if (databaseFile.exists()) {
            val dbStatusMsg = miniMessage.deserialize("<cyan><bold>💾</bold></cyan> <white>Existing database found. Claims enabled: <cyan>$claimsEnabled</cyan></white>")
            logger.info(PlainTextComponentSerializer.plainText().serialize(dbStatusMsg))

            var tempConnectionForMigration: Connection? = null
            try {
                tempConnectionForMigration = DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}")

                if (claimsEnabled) {
                    // Run migrations only if claims are enabled
                    val migrator = SQLiteMigrations(this, tempConnectionForMigration)
                    migrator.migrate()
                } else {
                    val skipMsg = miniMessage.deserialize("<yellow><bold>⏭️</bold></yellow> <white>Claims system disabled - skipping migrations but preserving existing guild data</white>")
                    logger.info(PlainTextComponentSerializer.plainText().serialize(skipMsg))
                }
            } finally {
                tempConnectionForMigration?.let {
                    try {
                        if (!it.isClosed) {
                            it.close()
                            logger.info("Closed temporary connection after database check.")
                        }
                    } catch (e: SQLException) {
                        val closeErrorMsg = miniMessage.deserialize("<red><bold>❌</bold></red> <white>Failed to close temporary database connection: ${e.message}</white>")
                        logger.severe(PlainTextComponentSerializer.plainText().serialize(closeErrorMsg))
                        e.printStackTrace()
                    }
                }
            }
        } else {
            // No existing database
            if (claimsEnabled) {
                val createDbMsg = miniMessage.deserialize("<orange><bold>🆕</bold></orange> <white>Database file not found. Creating a new database with full schema...</white>")
                logger.info(PlainTextComponentSerializer.plainText().serialize(createDbMsg))

                var newConnection: Connection? = null
                try {
                    // This will create the database file if it doesn't exist
                    newConnection = DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}")
                    val migrator = SQLiteMigrations(this, newConnection)
                    migrator.migrate()
                } catch (e: SQLException) {
                    val errorMsg = miniMessage.deserialize("<red><bold>❌</bold></red> <white>Failed to create new database or run migrations: ${e.message}</white>")
                    logger.severe(PlainTextComponentSerializer.plainText().serialize(errorMsg))
                    e.printStackTrace()
                } finally {
                    newConnection?.let {
                        try {
                            if (!it.isClosed) {
                                it.close()
                                logger.info("Closed connection for new database creation.")
                            }
                        } catch (e: SQLException) {
                            val closeErrorMsg = miniMessage.deserialize("<red><bold>❌</bold></red> <white>Failed to close new database connection: ${e.message}</white>")
                            logger.severe(PlainTextComponentSerializer.plainText().serialize(closeErrorMsg))
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
        // Use MiniMessage gradient tags for modern color formatting
        val gradientText = """
<gradient:#800080:#FF1493:#FF0000:#FFD700:#FFFF00>_                                ____         _  _      _</gradient>
<gradient:#FF1493:#800080:#FF1493:#FF0000:#FFD700>| |     _   _  _ __ ___    __ _  / ___| _   _ (_)| |  __| | ___</gradient>
<gradient:#FF0000:#FF1493:#800080:#FF1493:#FF0000>| |    | | | || '_ ` _ \\  / _` || |  _ | | | || || | / _` |/ ___</gradient>
<gradient:#FFD700:#FF0000:#FF1493:#800080:#FF1493>| |___ | |_| || | | | | || (_| || |_| || |_| || || || (_| |\\__ \\</gradient>
<gradient:#FFFF00:#FFD700:#FF0000:#FF1493:#800080>|_____| \\__,_||_| |_| |_| \\__,_| \\____| \\__,_||_||_| \\__,_||___/</gradient>
        """.trimIndent()

        // Log each line with MiniMessage formatting (automatically handles console compatibility)
        val lines = gradientText.lines()
        lines.forEach { line ->
            if (line.isNotBlank()) {
                val component = miniMessage.deserialize("[LumaGuilds] $line")
                logger.info(PlainTextComponentSerializer.plainText().serialize(component))
            }
        }

        // Use MiniMessage for all messages
        val enabledMsg = miniMessage.deserialize("<gradient:#4169E1:#00BFFF><bold>✨</bold> <white>LumaGuilds <aqua>v0.4.0 <white>has been <green>Enabled<white>!</gradient>")
        val enhancedMsg = miniMessage.deserialize("<gray>Enhanced guild system with parties, progression, and wars!</gray>")
        val attributionMsg = miniMessage.deserialize("<dark_gray>Made with <red><bold>♥</bold></red> by <gold>BadgersMC <dark_gray>& <aqua>mizarc</dark_gray>")

        logger.info(PlainTextComponentSerializer.plainText().serialize(enabledMsg))
        logger.info(PlainTextComponentSerializer.plainText().serialize(enhancedMsg))
        logger.info(PlainTextComponentSerializer.plainText().serialize(attributionMsg))
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

    override fun onDisable() {
        // Only cancel if pluginScope was initialized
        if (::pluginScope.isInitialized) {
            pluginScope.cancel()
        }

        // Cleanup any remaining temporary files
        try {
            get().get<FileExportManager>().cleanupOldFiles()
        } catch (e: Exception) {
            logger.warning("Failed to cleanup temporary files on disable: ${e.message}")
        }

        val disabledMsg = miniMessage.deserialize("<red><bold>❌</bold></red> <white>LumaGuilds has been <red>Disabled<white>!</white>")
        val thanksMsg = miniMessage.deserialize("<dark_gray>Thanks for using <gold>BadgersMC</gold> & <aqua>mizarc</aqua>'s plugin!</dark_gray>")
        logger.info(PlainTextComponentSerializer.plainText().serialize(disabledMsg))
        logger.info(PlainTextComponentSerializer.plainText().serialize(thanksMsg))
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
                val expansion = BellClaimsExpansion()
                if (expansion.register()) {
                    val papiSuccessMsg = miniMessage.deserialize("<green><bold>✓</bold></green> <white>Successfully registered <aqua>LumaGuilds</aqua> PlaceholderAPI expansion!</white>")
                    val papiPlaceholdersMsg = miniMessage.deserialize("<gray>Available placeholders: %bellclaims_guild_name%, %bellclaims_guild_tag%, etc.</gray>")
                    logger.info(PlainTextComponentSerializer.plainText().serialize(papiSuccessMsg))
                    logger.info(PlainTextComponentSerializer.plainText().serialize(papiPlaceholdersMsg))
                } else {
                    val papiFailMsg = miniMessage.deserialize("<red><bold>❌</bold></red> <white>Failed to register <aqua>LumaGuilds</aqua> PlaceholderAPI expansion!</white>")
                    logger.warning(PlainTextComponentSerializer.plainText().serialize(papiFailMsg))
                }
            } catch (e: Exception) {
                logger.severe("Error registering PlaceholderAPI expansion: ${e.message}")
                e.printStackTrace()
            }
        } else {
            val papiNotFoundMsg = miniMessage.deserialize("<yellow><bold>⚠</bold></yellow> <white>PlaceholderAPI not found. <aqua>LumaGuilds</aqua> placeholders will not be available.</white>")
            logger.info(PlainTextComponentSerializer.plainText().serialize(papiNotFoundMsg))
        }
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
        getCommand("bellclaims")?.setExecutor(BellClaimsCommand())
        getCommand("bellclaims")?.tabCompleter = BellClaimsCommand()
        val adminCmdMsg = miniMessage.deserialize("<green><bold>✓</bold></green> <white>Admin commands registered (/bellclaims)</white>")
        logger.info(PlainTextComponentSerializer.plainText().serialize(adminCmdMsg))
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
    }
}
