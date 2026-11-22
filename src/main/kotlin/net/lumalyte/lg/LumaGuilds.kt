package net.lumalyte.lg

import co.aikar.idb.Database
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lg.di.runtime.ActiveGuildRuntime
import net.lumalyte.lg.di.runtime.GuildRuntimeGraph
import net.lumalyte.lg.di.runtime.GuildRuntimeLifecycle
import net.lumalyte.lg.di.runtime.NexusGuildCommandRegistration
import net.lumalyte.lg.di.runtime.PaperGuildRuntime
import net.lumalyte.lg.infrastructure.persistence.migrations.MariaDBMigrations
import net.lumalyte.lg.infrastructure.persistence.migrations.SQLiteMigrations
import net.lumalyte.lg.infrastructure.persistence.storage.MariaDBStorage
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadMariaDBStorage
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import net.lumalyte.lg.infrastructure.services.ConfigServiceBukkit
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.logging.Level

/**
 * The entry point for the Luma Guilds plugin.
 *
 * Startup and shutdown are owned by exactly one [GuildRuntimeLifecycle] over one Nexus context
 * (REQ-NEXUS-CUTOVER-2/3). Koin and ACF are not started.
 */
class LumaGuilds : JavaPlugin() {
    internal lateinit var vaultProtectionListener: net.lumalyte.lg.infrastructure.listeners.VaultProtectionListener
    internal var enabledAtMillis: Long = 0L
        private set
    private val componentLogger = getComponentLogger()

    @Volatile
    private var runtime: GuildRuntimeLifecycle<Storage<Database>, ActiveGuildRuntime>? = null

    /**
     * Config and claims mode first; then the single runtime gates command parity, opens storage,
     * migrates, activates Nexus and registers services, integrations, listeners, commands and
     * schedules. A failure has already released everything acquired when it propagates.
     */
    override fun onEnable() {
        enabledAtMillis = System.currentTimeMillis()

        // Initialize PluginKeys with plugin instance (must be first)
        net.lumalyte.lg.common.PluginKeys.initialize(this)

        initConfig()

        // Claims mode is fixed before storage so migrations and the graph agree.
        val claimsEnabled = config.getBoolean("claims_enabled", true)

        val lifecycle = GuildRuntimeLifecycle(
            PaperGuildRuntime(this, claimsEnabled, NexusGuildCommandRegistration()),
        ) { resource, error ->
            logger.log(Level.WARNING, "Failed to release $resource during shutdown: ${error.message}", error)
        }
        runtime = lifecycle
        lifecycle.start()

        logConfigurationSummary()
        displayFancyStartupMessage()
    }

    override fun onDisable() {
        val lifecycle = runtime
        if (lifecycle == null) {
            logColored("🛑 LumaGuilds disabled before runtime startup")
            return
        }
        runtime = null
        lifecycle.stop()
        logColored("🛑 LumaGuilds disabled")
    }

    fun initConfig() {
        // Save default config if it doesn't exist
        saveDefaultConfig()
        net.lumalyte.lg.infrastructure.services.ConfigCompatibilityMigrator.migrate(dataFolder, logger)
        reloadConfig()
    }

    /**
     * Creates the appropriate storage instance based on configuration.
     * Returns either SQLiteStorage or MariaDBStorage with virtual thread support.
     */
    internal fun createStorage(claimsEnabled: Boolean): Storage<Database> {
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
                    logColored("Virtual threads are unavailable on this Java runtime")
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
                    logColored("Virtual threads are unavailable on this Java runtime")
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
                is SQLiteStorage, is VirtualThreadSQLiteStorage -> {
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
                is MariaDBStorage, is VirtualThreadMariaDBStorage -> {
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
     * Logs a message with proper console colors using Paper's component logger.
     */
    internal fun logColored(message: String) {
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
        logger.info("=== PREVIEWING STARTUP MESSAGES ===")
        displayFancyStartupMessage()
        logger.info("=== END PREVIEW ===")
    }

    /**
     * Test method to preview the secret easter egg (for developers only)
     */
    fun previewSecretEasterEgg() {
        logger.info("=== PREVIEWING SECRET EASTER EGG ===")
        try {
            val validator = net.lumalyte.lg.infrastructure.hidden.SystemValidator(pluginMeta.version)
            validator.validateAndDisplaySecretMessage()
        } catch (e: Exception) {
            logger.warning("Could not load secret easter egg: ${e.message}")
        }
        logger.info("=== END SECRET PREVIEW ===")
    }

    private fun graph(): GuildRuntimeGraph =
        runtime?.context?.graph ?: throw IllegalStateException("LumaGuilds runtime is not active")

    /**
     * Manually triggers daily war costs (for testing/admin use).
     */
    fun triggerDailyWarCosts(): Int {
        return try {
            val affectedGuilds = graph().dailyWarCostsService.applyDailyWarCosts()
            logColored("✓ Manual daily war costs applied to $affectedGuilds guilds")
            affectedGuilds
        } catch (e: Exception) {
            // Broad exception handling acceptable - manual trigger shouldn't crash server
            logColored("❌ Failed to apply daily war costs: ${e.message}")
            logger.log(Level.WARNING, "Manual daily war costs failed", e)
            0
        }
    }

    /**
     * Public service getters for external plugins (ARM-Guilds-Bridge, etc.), resolved from the
     * active runtime's singletons.
     */
    fun getGuildService(): net.lumalyte.lg.application.services.GuildService = graph().guildService

    fun getQuestService(): net.lumalyte.lg.application.services.QuestService = graph().questService

    fun getQuestRepository(): net.lumalyte.lg.application.persistence.QuestRepository = graph().questRepository

    fun getGuildVaultService(): net.lumalyte.lg.application.services.GuildVaultService = graph().guildVaultService

    fun getMemberService(): net.lumalyte.lg.application.services.MemberService = graph().memberService

    fun getRankService(): net.lumalyte.lg.application.services.RankService = graph().rankService

    fun getRelationService(): net.lumalyte.lg.application.services.RelationService = graph().relationService

    fun getPhysicalCurrencyService(): net.lumalyte.lg.application.services.PhysicalCurrencyService =
        graph().physicalCurrencyService
}
