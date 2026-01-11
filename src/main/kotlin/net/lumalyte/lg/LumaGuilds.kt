package net.lumalyte.lg

import co.aikar.commands.PaperCommandManager
import co.aikar.idb.Database
import net.lumalyte.lg.di.appModule
import net.lumalyte.lg.infrastructure.persistence.migrations.SQLiteMigrations
import net.lumalyte.lg.infrastructure.persistence.migrations.MariaDBMigrations
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import net.lumalyte.lg.infrastructure.persistence.storage.MariaDBStorage
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadMariaDBStorage
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
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
import java.io.IOException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException


/**
 * The entry point for the Luma Guilds plugin.
 */
class LumaGuilds : JavaPlugin() {
    private lateinit var commandManager: PaperCommandManager
    var metadata: Chat? = null // Nullable - only initialized if Vault plugin exists
    private lateinit var scheduler: BukkitScheduler
    lateinit var pluginScope: CoroutineScope
    private lateinit var dailyWarCostsScheduler: DailyWarCostsScheduler
    internal lateinit var vaultProtectionListener: net.lumalyte.lg.infrastructure.listeners.VaultProtectionListener
    private val componentLogger = getComponentLogger()


    override fun onEnable() {
        // Initialize PluginKeys with plugin instance (must be first)
        net.lumalyte.lg.common.PluginKeys.initialize(this)

        initConfig()

        // Check if claims are enabled BEFORE initializing database
        val claimsEnabled = config.getBoolean("claims_enabled", true)

        // Create storage based on config (SQLite or MariaDB)
        val storage = createStorage(claimsEnabled)

        // Initialize database with migrations
        initDatabase(storage, claimsEnabled)

        pluginScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scheduler = server.scheduler
        initLang()
        initialiseVaultDependency()
        initialisePlaceholderAPI()
        initialiseAxKothIntegration()
        commandManager = PaperCommandManager(this)

        // Enable case-insensitive command completion and parsing
        commandManager.enableUnstableAPI("help")

        // Start Koin with modular architecture
        startKoin { modules(appModule(this@LumaGuilds, storage, claimsEnabled)) }

        // Initialize Apollo AFTER Koin is started (requires Koin DI)
        initialiseApolloIntegration()

        // Initialize GuildInvitationManager with the repository from Koin
        net.lumalyte.lg.infrastructure.services.GuildInvitationManager.initialize(
            get().get<net.lumalyte.lg.application.persistence.GuildInvitationRepository>()
        )

        // Initialize Gold Balance Button
        net.lumalyte.lg.application.utilities.GoldBalanceButton.initialize(this, get().get())

        // Start vault auto-save service
        val vaultAutoSaveService = get().get<net.lumalyte.lg.application.services.VaultAutoSaveService>()
        vaultAutoSaveService.start()

        // Start vault backup service (auto-backup every 60 minutes by default)
        val vaultBackupService = get().get<net.lumalyte.lg.application.services.VaultBackupService>()
        vaultBackupService.startAutoBackup(intervalMinutes = 60)
        logColored("✓ Vault auto-backup started (interval: 60 minutes)")

        // Restore vault chests and holograms on server startup
        val vaultService = get().get<net.lumalyte.lg.application.services.GuildVaultService>()
        val hologramService = get().get<net.lumalyte.lg.infrastructure.services.VaultHologramService>()

        // Restore physical chest blocks at saved locations
        val restoredChests = vaultService.restoreAllVaultChests()

        // Start hologram service and recreate all holograms
        hologramService.start()
        hologramService.recreateAllHolograms()

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
        registerNonClaimEvents(claimsEnabled)

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

    /**
     * Creates the appropriate storage instance based on configuration.
     * Returns either SQLiteStorage or MariaDBStorage with virtual thread support.
     */
    private fun createStorage(claimsEnabled: Boolean): Storage<Database> {
        val databaseType = config.getString("database_type", "sqlite")?.lowercase() ?: "sqlite"
        val useVirtualThreads = config.getBoolean("database.use_virtual_threads", true)

        return when (databaseType) {
            "mariadb", "mysql" -> {
                logColored("🔌 Connecting to MariaDB database...")
                val host = config.getString("mariadb.host", "localhost") ?: "localhost"
                val port = config.getInt("mariadb.port", 3306)
                val database = config.getString("mariadb.database", "lumaguilds") ?: "lumaguilds"
                val username = config.getString("mariadb.username", "root") ?: "root"
                val password = config.getString("mariadb.password", "password") ?: "password"

                // Virtual threads allow much higher pool sizes
                val maxPoolSize = config.getInt("mariadb.pool.maximum_pool_size", if (useVirtualThreads) 50 else 10)
                val minIdle = config.getInt("mariadb.pool.minimum_idle", if (useVirtualThreads) 10 else 2)
                val connectionTimeout = config.getLong("mariadb.pool.connection_timeout", 30000)
                val idleTimeout = config.getLong("mariadb.pool.idle_timeout", 600000)
                val maxLifetime = config.getLong("mariadb.pool.max_lifetime", 1800000)

                try {
                    val storage = if (useVirtualThreads) {
                        logColored("⚡ Using virtual threads for database operations")
                        VirtualThreadMariaDBStorage(
                            host = host,
                            port = port,
                            database = database,
                            username = username,
                            password = password,
                            maxPoolSize = maxPoolSize,
                            minIdle = minIdle,
                            connectionTimeout = connectionTimeout,
                            idleTimeout = idleTimeout,
                            maxLifetime = maxLifetime
                        )
                    } else {
                        MariaDBStorage(
                            host = host,
                            port = port,
                            database = database,
                            username = username,
                            password = password,
                            maxPoolSize = maxPoolSize,
                            minIdle = minIdle,
                            connectionTimeout = connectionTimeout,
                            idleTimeout = idleTimeout,
                            maxLifetime = maxLifetime
                        )
                    }
                    logColored("✓ Successfully connected to MariaDB at $host:$port/$database")
                    storage
                } catch (e: SQLException) {
                    logColored("❌ Failed to connect to MariaDB: ${e.message}")
                    e.printStackTrace()
                    throw RuntimeException("Failed to initialize MariaDB storage - database connection error", e)
                } catch (e: IllegalArgumentException) {
                    logColored("❌ Invalid MariaDB configuration: ${e.message}")
                    e.printStackTrace()
                    throw RuntimeException("Failed to initialize MariaDB storage - invalid configuration", e)
                } catch (e: NoSuchMethodError) {
                    logColored("⚠ Virtual threads not available - falling back to platform threads")
                    logColored("Upgrade to Java 21+ for better performance")
                    MariaDBStorage(
                        host = host,
                        port = port,
                        database = database,
                        username = username,
                        password = password,
                        maxPoolSize = 10,
                        minIdle = 2,
                        connectionTimeout = connectionTimeout,
                        idleTimeout = idleTimeout,
                        maxLifetime = maxLifetime
                    )
                }
            }
            else -> {
                logColored("💾 Using SQLite database...")
                try {
                    val storage = if (useVirtualThreads) {
                        logColored("⚡ Using virtual threads for database operations")
                        VirtualThreadSQLiteStorage(dataFolder)
                    } else {
                        SQLiteStorage(dataFolder)
                    }
                    val databaseFile = File(dataFolder, "lumaguilds.db")
                    logColored("✓ SQLite database initialized at ${databaseFile.absolutePath}")
                    storage
                } catch (e: SQLException) {
                    logColored("❌ Failed to initialize SQLite database: ${e.message}")
                    e.printStackTrace()
                    throw RuntimeException("Failed to initialize SQLite storage - database error", e)
                } catch (e: IOException) {
                    logColored("❌ Failed to create SQLite database file: ${e.message}")
                    e.printStackTrace()
                    throw RuntimeException("Failed to initialize SQLite storage - file system error", e)
                } catch (e: NoSuchMethodError) {
                    logColored("⚠ Virtual threads not available - falling back to platform threads")
                    logColored("Upgrade to Java 21+ for better performance")
                    SQLiteStorage(dataFolder)
                }
            }
        }
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

    /**
     * Initializes the database with migrations based on storage type.
     * For SQLite: uses SQLiteMigrations
     * For MariaDB: uses MariaDBMigrations
     */
    fun initDatabase(storage: Storage<Database>, claimsEnabled: Boolean) {
        // Ensure the plugin data folder exists
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
            logger.info("Created plugin data folder: ${dataFolder.absolutePath}")
        }

        try {
            when (storage) {
                is SQLiteStorage -> {
                    logColored("🔄 Running SQLite migrations...")

                    try {
                        // Use the storage's HikariCP connection to ensure schema changes are visible
                        storage.connection.getConnection().use { connection ->
                            val migrator = SQLiteMigrations(this, connection, claimsEnabled)
                            migrator.migrate()
                        }
                        logColored("✓ SQLite migrations completed successfully")
                    } catch (e: SQLException) {
                        logColored("❌ Failed to run SQLite migrations: ${e.message}")
                        e.printStackTrace()
                        throw RuntimeException("Failed to run SQLite migrations - database error", e)
                    }
                }
                is MariaDBStorage -> {
                    logColored("🔄 Running MariaDB migrations...")
                    // Get MariaDB connection details from config for migration
                    val host = config.getString("mariadb.host", "localhost") ?: "localhost"
                    val port = config.getInt("mariadb.port", 3306)
                    val database = config.getString("mariadb.database", "lumaguilds") ?: "lumaguilds"
                    val username = config.getString("mariadb.username", "root") ?: "root"
                    val password = config.getString("mariadb.password", "password") ?: "password"

                    val jdbcUrl = "jdbc:mariadb://$host:$port/$database?useSSL=false&allowPublicKeyRetrieval=true"
                    var migrationConnection: Connection? = null
                    try {
                        migrationConnection = DriverManager.getConnection(jdbcUrl, username, password)
                        val migrator = MariaDBMigrations(this, migrationConnection)
                        migrator.migrate()
                        logColored("✓ MariaDB migrations completed successfully")
                    } finally {
                        migrationConnection?.let {
                            try {
                                if (!it.isClosed) {
                                    it.close()
                                }
                            } catch (e: SQLException) {
                                logColored("❌ Failed to close migration connection: ${e.message}")
                            }
                        }
                    }
                }
                else -> {
                    logColored("⚠ Unknown storage type: ${storage::class.simpleName}")
                }
            }
        } catch (e: RuntimeException) {
            // Re-throw RuntimeException from migration failures
            throw e
        } catch (e: SQLException) {
            logColored("❌ Unexpected database error during initialization: ${e.message}")
            e.printStackTrace()
            throw RuntimeException("Failed to initialize database - unexpected SQL error", e)
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
                val validator = net.lumalyte.lg.infrastructure.hidden.SystemValidator(pluginMeta.version)
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
     * Parses a priority string into an EventPriority enum value.
     * Valid values: LOWEST, LOW, NORMAL, HIGH, HIGHEST, MONITOR
     * Defaults to NORMAL if invalid.
     */
    private fun parsePriority(priorityString: String): org.bukkit.event.EventPriority {
        return when (priorityString.uppercase()) {
            "LOWEST" -> org.bukkit.event.EventPriority.LOWEST
            "LOW" -> org.bukkit.event.EventPriority.LOW
            "NORMAL" -> org.bukkit.event.EventPriority.NORMAL
            "HIGH" -> org.bukkit.event.EventPriority.HIGH
            "HIGHEST" -> org.bukkit.event.EventPriority.HIGHEST
            "MONITOR" -> org.bukkit.event.EventPriority.MONITOR
            else -> {
                componentLogger.warn(Component.text("⚠ Invalid event priority '$priorityString', defaulting to NORMAL").color(NamedTextColor.YELLOW))
                org.bukkit.event.EventPriority.NORMAL
            }
        }
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
     * Displays the main gradient-colored startup text with one color per line.
     * Creates a Vice City-style aesthetic.
     */
    private fun displayGradientText() {
        // Simple ASCII art for LumaGuilds
        val asciiLines = listOf(
            " _                                ____         _  _      _      ",
            "| |     _   _  _ __ ___    __ _  / ___| _   _ (_)| |  __| | ___ ",
            "| |    | | | || '_ ` _ \\  / _` || |  _ | | | || || | / _` |/ __|",
            "| |___ | |_| || | | | | || (_| || |_| || |_| || || || (_| |\\__ \\",
            "|_____| \\__,_||_| |_| |_| \\__,_| \\____| \\__,_||_||_| \\__,_||___/"
        )

        // Gradient colors from cyan to magenta (one color per line)
        // ANSI 256-color codes: 51 (cyan) -> 201 (magenta)
        val lineColors = listOf(51, 123, 165, 177, 201)

        // Apply one color per line
        asciiLines.forEachIndexed { index, line ->
            val colorCode = lineColors[index]
            server.consoleSender.sendMessage("\u001B[38;5;${colorCode}m$line\u001B[0m")
        }

        // Simple text messages
        logColored("✨ LumaGuilds v${description.version} has been Enabled!")
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
            val validator = net.lumalyte.lg.infrastructure.hidden.SystemValidator(pluginMeta.version)
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
                // Broad exception handling acceptable here - optional integration shouldn't crash plugin
                // Can fail due to: NoClassDefFoundError, LinkageError, version incompatibilities
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
                // Broad exception handling acceptable here - optional integration shouldn't crash plugin
                // Can fail due to: NoClassDefFoundError, LinkageError, API changes
                logger.severe("Error registering AxKoth integration: ${e.message}")
                e.printStackTrace()
            }
        } else {
            logColored("⚠ AxKoth not found. Guild KOTH integration unavailable.")
        }
    }

    /**
     * Initializes Apollo (Lunar Client) integration if available.
     * Provides enhanced features for Lunar Client users through Apollo API.
     */
    private fun initialiseApolloIntegration() {
        if (!config.getBoolean("apollo.enabled", true)) {
            logColored("⚠ Apollo integration disabled in config")
            return
        }

        if (Bukkit.getPluginManager().getPlugin("Apollo-Bukkit") != null) {
            try {
                val lunarClientService = get().getOrNull<net.lumalyte.lg.application.services.apollo.LunarClientService>()
                if (lunarClientService != null && lunarClientService.isApolloAvailable()) {
                    logColored("✓ Successfully initialized Apollo (Lunar Client) integration!")
                    logColored("Enhanced features available for Lunar Client users")

                    // Log enabled features and start services
                    if (config.getBoolean("apollo.teams.enabled", true)) {
                        logColored("  ⭐ Teams Module: Guild members visible on minimap/HUD")

                        // Start GuildTeamService
                        val guildTeamService = get().getOrNull<net.lumalyte.lg.infrastructure.services.apollo.GuildTeamService>()
                        guildTeamService?.start()

                        // Register GuildTeamListener
                        val guildTeamListener = get().getOrNull<net.lumalyte.lg.infrastructure.listeners.apollo.GuildTeamListener>()
                        if (guildTeamListener != null) {
                            server.pluginManager.registerEvents(guildTeamListener, this)
                        }
                    }
                    if (config.getBoolean("apollo.waypoints.enabled", true)) {
                        logColored("  📍 Waypoints Module: Guild home navigation")
                    }
                    if (config.getBoolean("apollo.beams.enabled", true)) {
                        logColored("  ⚡ Beams Module: Vault location markers")
                    }
                    if (config.getBoolean("apollo.borders.enabled", true)) {
                        logColored("  🔲 Borders Module: War territory visualization")
                    }
                    if (config.getBoolean("apollo.notifications.enabled", true)) {
                        logColored("  📢 Notifications Module: Rich guild event alerts")

                        // Register GuildNotificationListener
                        val notificationListener = get().getOrNull<net.lumalyte.lg.infrastructure.listeners.apollo.GuildNotificationListener>()
                        if (notificationListener != null) {
                            server.pluginManager.registerEvents(notificationListener, this)
                        }
                    }
                    if (config.getBoolean("apollo.richpresence.enabled", true)) {
                        logColored("  👥 Rich Presence Module: Discord/Launcher guild status")

                        // Register GuildRichPresenceListener
                        val richPresenceListener = get().getOrNull<net.lumalyte.lg.infrastructure.listeners.apollo.GuildRichPresenceListener>()
                        if (richPresenceListener != null) {
                            server.pluginManager.registerEvents(richPresenceListener, this)
                        }
                    }
                } else {
                    logColored("⚠ Apollo API not responding - features disabled")
                }
            } catch (e: Exception) {
                // Broad exception handling acceptable here - optional integration shouldn't crash plugin
                // Can fail due to: NoClassDefFoundError, LinkageError, API version mismatch
                logger.severe("Error initializing Apollo integration: ${e.message}")
                e.printStackTrace()
            }
        } else {
            logColored("⚠ Apollo-Bukkit plugin not found. Lunar Client features unavailable.")
            logColored("  Install Apollo-Bukkit from https://modrinth.com/plugin/lunar-client-apollo")
        }
    }

    /**
     * Configures basic command completions using ACF's built-in system.
     * ACF automatically handles tab completion through @CommandCompletion annotations
     * on command methods, providing case-insensitive completion out of the box.
     */
    private fun configureCommandCompletions() {
        // ACF handles tab completion automatically through @CommandCompletion annotations
        // Register custom async completions for dynamic data

        // Register custom player name completion for all online players
        commandManager.commandCompletions.registerAsyncCompletion("allplayers") { context ->
            Bukkit.getOnlinePlayers().map { it.name }
        }

        // Register unlocked emojis completion (shows only emojis the player has permission to use)
        commandManager.commandCompletions.registerAsyncCompletion("unlockedemojis") { context ->
            val player = context.player ?: return@registerAsyncCompletion emptyList()
            val nexoEmojiService = get().get<net.lumalyte.lg.infrastructure.services.NexoEmojiService>()

            // Get emoji names player has permission for and format as :emojiname:
            nexoEmojiService.getPlayerUnlockedEmojis(player).map { emojiName ->
                ":$emojiName:"
            }
        }

        // Register parties completion (shows available parties for the player)
        commandManager.commandCompletions.registerAsyncCompletion("parties") { context ->
            val player = context.player ?: return@registerAsyncCompletion listOf("GLOBAL")
            val playerId = player.uniqueId

            val partyService = get().get<net.lumalyte.lg.application.services.PartyService>()
            val guildService = get().get<net.lumalyte.lg.application.services.GuildService>()

            // Get player's guild IDs
            val playerGuildIds = guildService.getPlayerGuilds(playerId).map { it.id }.toSet()

            if (playerGuildIds.isEmpty()) {
                return@registerAsyncCompletion listOf("GLOBAL")
            }

            // Get all active parties for player's guilds
            val activeParties = playerGuildIds.flatMap { guildId ->
                partyService.getActivePartiesForGuild(guildId)
            }.toSet()

            // Filter out parties the player is banned from and get their names
            val partyNames = activeParties
                .filter { party -> !party.isPlayerBanned(playerId) }
                .mapNotNull { party -> party.name }
                .sorted()

            // Always include GLOBAL option first
            listOf("GLOBAL") + partyNames
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
        getCommand("lumaguilds")?.setExecutor(LumaGuildsCommand())
        getCommand("lumaguilds")?.tabCompleter = LumaGuildsCommand()

        // Register Bedrock cache stats command
        getCommand("bedrockcachestats")?.setExecutor(BedrockCacheStatsCommand())

        // Register Apollo debug command
        getCommand("apollodebug")?.setExecutor(net.lumalyte.lg.interaction.commands.ApolloDebugCommand())

        // Register Vault Rollback admin command
        val vaultRollbackCommand = net.lumalyte.lg.interaction.commands.admin.VaultRollbackCommand(
            get().get(),
            get().get()
        )
        getCommand("vaultrollback")?.setExecutor(vaultRollbackCommand)
        getCommand("vaultrollback")?.tabCompleter = vaultRollbackCommand

        // Register Bank Credit admin command
        val bankCreditCommand = net.lumalyte.lg.interaction.commands.admin.BankCreditCommand(
            get().get()
        )
        getCommand("bankcredit")?.setExecutor(bankCreditCommand)
        getCommand("bankcredit")?.tabCompleter = bankCreditCommand

        // Register Remove Vault admin command
        val removeVaultCommand = net.lumalyte.lg.interaction.commands.admin.RemoveVaultCommand(
            get().get(),
            get().get()
        )
        getCommand("removevault")?.setExecutor(removeVaultCommand)
        getCommand("removevault")?.tabCompleter = removeVaultCommand

        logColored("✓ Admin commands registered (/lumaguilds, /bellclaims, /vaultrollback, /bankcredit, /removevault)")
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
    private fun registerNonClaimEvents(claimsEnabled: Boolean) {
        // Use the DI instance of ChatInputListener to avoid duplicate instances
        val chatInputListener = get().get<ChatInputListener>()
        server.pluginManager.registerEvents(chatInputListener, this)

        // Register party chat listener with configurable priority (auto-routes messages to party after /pc switch)
        val config = get().get<ConfigService>().loadConfig()
        val partyChatListener = PartyChatListener()
        val priority = parsePriority(config.party.partyChatListenerPriority)
        server.pluginManager.registerEvent(
            io.papermc.paper.event.player.AsyncChatEvent::class.java,
            partyChatListener,
            priority,
            { _, event -> partyChatListener.onPlayerChat(event as io.papermc.paper.event.player.AsyncChatEvent) },
            this,
            false // ignoreCancelled - MUST be false to ensure we process events even if ChatControl marks them cancelled
        )
        logColored("✓ Party chat auto-routing registered (priority: ${config.party.partyChatListenerPriority})")

        server.pluginManager.registerEvents(BannerSelectionListener(), this)
        server.pluginManager.registerEvents(BannerFuelPreventionListener(), this)

        // Register progression event listener
        val progressionEventListener = get().get<ProgressionEventListener>()
        server.pluginManager.registerEvents(progressionEventListener, this)

        // Register guild channel creation listener (for creating default channels)
        val guildChannelCreationListener = get().get<net.lumalyte.lg.infrastructure.listeners.GuildChannelCreationListener>()
        server.pluginManager.registerEvents(guildChannelCreationListener, this)

        // Register vault protection listener
        vaultProtectionListener = net.lumalyte.lg.infrastructure.listeners.VaultProtectionListener()
        server.pluginManager.registerEvents(vaultProtectionListener, this)

        // Register vault inventory listener (for gold button and real-time sync)
        val vaultInventoryListener = get().get<net.lumalyte.lg.interaction.listeners.VaultInventoryListener>()
        server.pluginManager.registerEvents(vaultInventoryListener, this)

        // Register player session cleanup listener
        server.pluginManager.registerEvents(net.lumalyte.lg.infrastructure.listeners.PlayerSessionListener(), this)

        // Register war kill tracking listener
        server.pluginManager.registerEvents(net.lumalyte.lg.infrastructure.listeners.WarKillTrackingListener(), this)

        // Register admin override listener (for logout cleanup) - only when claims enabled
        if (claimsEnabled) {
            val adminOverrideListener = get().get<net.lumalyte.lg.interaction.listeners.AdminOverrideListener>()
            server.pluginManager.registerEvents(adminOverrideListener, this)
        }
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
            // Broad exception handling acceptable - scheduler failure shouldn't prevent plugin load
            logColored("❌ Failed to initialize daily war costs scheduler: ${e.message}")
            e.printStackTrace()
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
            // Broad exception handling acceptable - manual trigger shouldn't crash server
            logColored("❌ Failed to apply daily war costs: ${e.message}")
            e.printStackTrace()
            0
        }
    }

    /**
     * Public service getters for external plugins (ARM-Guilds-Bridge, etc.)
     * These methods provide access to LumaGuilds services via Koin dependency injection
     */
    fun getGuildService(): net.lumalyte.lg.application.services.GuildService {
        return get().get()
    }

    fun getGuildVaultService(): net.lumalyte.lg.application.services.GuildVaultService {
        return get().get()
    }

    fun getMemberService(): net.lumalyte.lg.application.services.MemberService {
        return get().get()
    }

    fun getRankService(): net.lumalyte.lg.application.services.RankService {
        return get().get()
    }

    fun getRelationService(): net.lumalyte.lg.application.services.RelationService {
        return get().get()
    }

    fun getPhysicalCurrencyService(): net.lumalyte.lg.application.services.PhysicalCurrencyService {
        return get().get()
    }

    override fun onDisable() {
        // Stop Apollo services
        try {
            val guildTeamService = get().getOrNull<net.lumalyte.lg.infrastructure.services.apollo.GuildTeamService>()
            guildTeamService?.stop()
        } catch (e: Exception) {
            // Broad exception handling acceptable - shutdown should be resilient
            logger.warning("Failed to stop guild team service: ${e.message}")
        }

        // Stop vault auto-save service (this will flush all pending writes)
        try {
            val vaultAutoSaveService = get().get<net.lumalyte.lg.application.services.VaultAutoSaveService>()
            vaultAutoSaveService.stop()
        } catch (e: Exception) {
            // Broad exception handling acceptable - shutdown should be resilient
            logger.severe("Failed to stop vault auto-save service: ${e.message}")
            e.printStackTrace()
        }

        // Stop vault backup service
        try {
            val vaultBackupService = get().get<net.lumalyte.lg.application.services.VaultBackupService>()
            vaultBackupService.stopAutoBackup()
        } catch (e: Exception) {
            // Broad exception handling acceptable - shutdown should be resilient
            logger.severe("Failed to stop vault backup service: ${e.message}")
            e.printStackTrace()
        }

        // Stop vault hologram service
        try {
            val hologramService = get().get<net.lumalyte.lg.infrastructure.services.VaultHologramService>()
            hologramService.stop()
        } catch (e: Exception) {
            // Broad exception handling acceptable - shutdown should be resilient
            logger.severe("Failed to stop vault hologram service: ${e.message}")
            e.printStackTrace()
        }

        // Stop the daily war costs scheduler
        if (::dailyWarCostsScheduler.isInitialized) {
            dailyWarCostsScheduler.stopDailyScheduler()
        }

        // Shutdown virtual thread executor
        try {
            val virtualExecutor = get().getOrNull<java.util.concurrent.ExecutorService>(
                org.koin.core.qualifier.named("VirtualThreadExecutor")
            )
            virtualExecutor?.shutdown()
            // Wait up to 5 seconds for termination
            if (virtualExecutor?.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS) == false) {
                virtualExecutor.shutdownNow()
            }
        } catch (e: Exception) {
            logger.warning("Failed to shutdown virtual thread executor: ${e.message}")
        }

        // Cancel any remaining plugin scope tasks
        if (::pluginScope.isInitialized) {
            pluginScope.cancel()
        }

        logColored("🛑 LumaGuilds disabled")
    }
}
