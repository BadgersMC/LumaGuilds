package net.lumalyte.lg.infrastructure.hytale.services

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.config.MainConfig
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Hytale implementation of ConfigService.
 *
 * This service loads and manages the plugin configuration using JSON format.
 *
 * Note: The original Paper implementation used YAML, but Hytale doesn't have a built-in
 * YAML parser. We use JSON with Gson for simplicity and performance.
 *
 * Configuration file location: <dataDirectory>/config.json
 *
 * If the config file doesn't exist, a default configuration is created with sensible defaults.
 */
class HytaleConfigService(
    private val dataDirectory: Path
) : ConfigService {

    private val log = LoggerFactory.getLogger(HytaleConfigService::class.java)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val configFile: File = dataDirectory.resolve("config.json").toFile()

    private var cachedConfig: MainConfig? = null

    override fun loadConfig(): MainConfig {
        // Return cached config if available
        cachedConfig?.let { return it }

        try {
            // Create data directory if it doesn't exist
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory)
                log.info("Created data directory: $dataDirectory")
            }

            // Load or create config
            val config = if (configFile.exists()) {
                loadConfigFromFile()
            } else {
                createDefaultConfig()
            }

            cachedConfig = config
            return config
        } catch (e: Exception) {
            log.error("Failed to load configuration! Using defaults.", e)
            val defaultConfig = MainConfig()
            cachedConfig = defaultConfig
            return defaultConfig
        }
    }

    /**
     * Loads configuration from the JSON file.
     */
    private fun loadConfigFromFile(): MainConfig {
        try {
            val json = configFile.readText()
            val config = gson.fromJson(json, MainConfig::class.java)
            log.info("Loaded configuration from: ${configFile.absolutePath}")
            return config
        } catch (e: Exception) {
            log.error("Failed to parse config file! Creating backup and using defaults.", e)

            // Backup corrupted config
            val backupFile = File(configFile.parent, "config.json.backup")
            configFile.copyTo(backupFile, overwrite = true)
            log.warn("Backed up corrupted config to: ${backupFile.absolutePath}")

            // Create new default config
            return createDefaultConfig()
        }
    }

    /**
     * Creates a default configuration file with sensible defaults.
     */
    private fun createDefaultConfig(): MainConfig {
        val defaultConfig = MainConfig(
            // Database
            databaseType = "sqlite",

            // Claims
            claimsEnabled = true,
            partiesEnabled = true,
            claimLimit = 10,
            claimBlockLimit = 10000,
            initialClaimSize = 10,
            minimumPartitionSize = 5,
            distanceBetweenClaims = 0,
            visualiserHideDelayPeriod = 1.0,
            visualiserRefreshPeriod = 0.5,
            rightClickHarvest = true,

            // Localization
            pluginLanguage = "EN",
            customClaimToolModelId = 732000,
            customMoveToolModelId = 732001
        )

        try {
            // Write default config to file
            val json = gson.toJson(defaultConfig)
            configFile.writeText(json)
            log.info("Created default configuration at: ${configFile.absolutePath}")
        } catch (e: Exception) {
            log.error("Failed to write default configuration file!", e)
        }

        return defaultConfig
    }

    /**
     * Reloads the configuration from disk.
     * Clears the cache and loads fresh configuration.
     */
    fun reloadConfig(): MainConfig {
        cachedConfig = null
        return loadConfig()
    }

    /**
     * Saves the current configuration to disk.
     *
     * @param config The configuration to save
     * @return true if successful, false otherwise
     */
    fun saveConfig(config: MainConfig): Boolean {
        return try {
            val json = gson.toJson(config)
            configFile.writeText(json)
            cachedConfig = config
            log.info("Saved configuration to: ${configFile.absolutePath}")
            true
        } catch (e: Exception) {
            log.error("Failed to save configuration!", e)
            false
        }
    }
}
