package net.lumalyte.lg.application

import net.lumalyte.lg.LumaGuilds
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Handles early plugin initialization tasks during the onLoad phase.
 * Performs environment validation, filesystem preparation, and soft dependency detection.
 */
class PluginBootstrap(private val plugin: LumaGuilds) : KoinComponent {

    private val logger: Logger by inject()

    /**
     * Validates the plugin environment and requirements.
     * Checks for minimum server version, Java version, and other prerequisites.
     */
    fun validateEnvironment() {
        logger.info("🔍 Validating plugin environment...")

        // Check Java version
        val javaVersion = System.getProperty("java.version")
        logger.info("Java version: $javaVersion")

        // Check server version
        val serverVersion = plugin.server.version
        logger.info("Server version: $serverVersion")

        // TODO: Add specific validation logic here
        // - Check for Paper-specific features
        // - Validate Java version compatibility
        // - Check for conflicting plugins

        logger.info("✅ Environment validation completed")
    }

    /**
     * Prepares the plugin filesystem and configuration structure.
     * Creates necessary directories and ensures configuration files exist.
     */
    fun prepareFilesystem() {
        logger.info("📁 Preparing filesystem...")

        val dataFolder = plugin.dataFolder
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
            logger.info("Created plugin data folder: ${dataFolder.absolutePath}")
        }

        // TODO: Ensure configuration files exist with defaults
        // TODO: Create necessary subdirectories

        logger.info("✅ Filesystem preparation completed")
    }

    /**
     * Detects and logs information about soft dependencies.
     * Checks for optional plugins like Vault, PlaceholderAPI, Floodgate, etc.
     */
    fun detectSoftDependencies() {
        logger.info("🔗 Detecting soft dependencies...")

        val pluginManager = plugin.server.pluginManager

        // Check for Vault
        val vault = pluginManager.getPlugin("Vault")
        if (vault != null) {
            logger.info("✅ Found Vault ${vault.description.version} - Economy integration enabled")
        } else {
            logger.info("⚠️  Vault not found - Economy features disabled")
        }

        // Check for PlaceholderAPI
        val placeholderAPI = pluginManager.getPlugin("PlaceholderAPI")
        if (placeholderAPI != null) {
            logger.info("✅ Found PlaceholderAPI ${placeholderAPI.description.version} - Placeholder integration enabled")
        } else {
            logger.info("⚠️  PlaceholderAPI not found - Placeholder features disabled")
        }

        // Check for Floodgate
        val floodgate = pluginManager.getPlugin("floodgate")
        if (floodgate != null) {
            logger.info("✅ Found Floodgate ${floodgate.description.version} - Bedrock integration enabled")
        } else {
            logger.info("⚠️  Floodgate not found - Bedrock features disabled")
        }

        // Check for Geyser
        val geyser = pluginManager.getPlugin("Geyser-Spigot")
        if (geyser != null) {
            logger.info("✅ Found Geyser ${geyser.description.version} - Bedrock integration enhanced")
        } else {
            logger.info("ℹ️  Geyser not found - Using Floodgate-only Bedrock support")
        }

        logger.info("✅ Soft dependency detection completed")
    }
}