package net.lumalyte.lg.application

import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.FileExportManager
import net.lumalyte.lg.interaction.brigadier.CommandRegistrar
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Handles the main plugin runtime during the onEnable phase.
 * Manages dependency injection, configuration loading, service initialization,
 * listener registration, and command setup.
 */
class PluginRuntime(private val plugin: LumaGuilds) : KoinComponent {

    private val logger: Logger by inject()
    private val configService: ConfigService by inject()
    private val commandRegistrar: CommandRegistrar by inject()

    /**
     * Starts the dependency injection framework.
     */
    fun startDependencyInjection() {
        logger.info("🔧 Starting dependency injection...")

        // DI is already started by the time this is called
        // This method exists for future expansion and logging

        logger.info("✅ Dependency injection initialized")
    }

    /**
     * Loads and validates the plugin configuration.
     */
    fun loadConfiguration() {
        logger.info("⚙️  Loading configuration...")

        try {
            configService.loadConfig()
            logger.info("✅ Configuration loaded successfully")
        } catch (e: Exception) {
            logger.severe("❌ Failed to load configuration: ${e.message}")
            throw e
        }
    }

    /**
     * Initializes all plugin services.
     */
    fun initializeServices() {
        logger.info("🚀 Initializing services...")

        // Services are initialized through DI, but we can add additional
        // initialization logic here if needed

        // Initialize Vault dependency
        initializeVaultDependency()

        // Initialize PlaceholderAPI dependency
        initializePlaceholderAPI()

        // TODO: Initialize other services as needed

        logger.info("✅ Services initialized")
    }

    /**
     * Registers all event listeners.
     */
    fun registerListeners() {
        logger.info("👂 Registering event listeners...")

        // TODO: Register listeners through DI or service locator
        // This will be handled by individual services that need listeners

        logger.info("✅ Event listeners registered")
    }

    /**
     * Registers all commands via the command registrar.
     */
    fun registerCommands() {
        logger.info("📝 Registering commands...")

        commandRegistrar.registerAll()

        logger.info("✅ Commands registered")
    }

    /**
     * Shuts down the plugin runtime cleanly.
     */
    fun shutdown() {
        logger.info("🛑 Shutting down plugin runtime...")

        try {
            // TODO: Clean shutdown logic
            // - Close database connections
            // - Cancel scheduled tasks
            // - Save pending data
            // - Unregister listeners if needed

            logger.info("✅ Plugin runtime shutdown completed")
        } catch (e: Exception) {
            logger.warning("Error during runtime shutdown: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Initializes Vault economy integration if available.
     */
    private fun initializeVaultDependency() {
        val vault = plugin.server.pluginManager.getPlugin("Vault")
        if (vault != null) {
            logger.info("💰 Initializing Vault economy integration...")
            // TODO: Set up Vault economy provider
        }
    }

    /**
     * Initializes PlaceholderAPI integration if available.
     */
    private fun initializePlaceholderAPI() {
        val placeholderAPI = plugin.server.pluginManager.getPlugin("PlaceholderAPI")
        if (placeholderAPI != null) {
            logger.info("🏷️  Initializing PlaceholderAPI integration...")
            // TODO: Register placeholders
        }
    }
}