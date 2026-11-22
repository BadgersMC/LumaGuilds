package net.badgersmc.nexus.config

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Central manager for all configuration files in an application.
 * Provides loading, saving, and reloading capabilities with error handling.
 */
class ConfigManager(configDirectory: Path) {

    private val loader = ConfigLoader(configDirectory)
    private val configs = ConcurrentHashMap<KClass<*>, Any>()

    /**
     * Load a configuration file and cache it.
     * If already loaded, returns the cached instance.
     */
    fun <T : Any> load(configClass: KClass<T>): T {
        @Suppress("UNCHECKED_CAST")
        return configs.computeIfAbsent(configClass) {
            try {
                loader.load(configClass)
            } catch (e: Exception) {
                System.err.println("Failed to load config ${configClass.simpleName}: ${e.message}")
                throw ConfigException("Failed to load ${configClass.simpleName}", e)
            }
        } as T
    }

    /**
     * Load a configuration file (reified version for easier usage).
     */
    inline fun <reified T : Any> load(): T {
        return load(T::class)
    }

    /**
     * Get a previously loaded configuration.
     * Throws if not yet loaded.
     */
    fun <T : Any> get(configClass: KClass<T>): T {
        @Suppress("UNCHECKED_CAST")
        return configs[configClass] as? T
            ?: throw ConfigException("Config ${configClass.simpleName} not loaded. Call load() first.")
    }

    /**
     * Get a previously loaded configuration (reified version).
     */
    inline fun <reified T : Any> get(): T {
        return get(T::class)
    }

    /**
     * Save a configuration to file.
     */
    fun <T : Any> save(config: T) {
        try {
            loader.save(config)
            configs[config::class] = config
        } catch (e: Exception) {
            System.err.println("Failed to save config ${config::class.simpleName}: ${e.message}")
            throw ConfigException("Failed to save ${config::class.simpleName}", e)
        }
    }

    /**
     * Reload a configuration from file.
     */
    fun <T : Any> reload(configClass: KClass<T>) {
        val config = configs[configClass]
            ?: throw ConfigException("Config ${configClass.simpleName} not loaded. Call load() first.")

        try {
            @Suppress("UNCHECKED_CAST")
            loader.reload(config as T)
        } catch (e: Exception) {
            System.err.println("Failed to reload config ${configClass.simpleName}: ${e.message}")
            throw ConfigException("Failed to reload ${configClass.simpleName}", e)
        }
    }

    /**
     * Reload a configuration (reified version).
     */
    inline fun <reified T : Any> reload() {
        reload(T::class)
    }

    /**
     * Reload all loaded configurations.
     */
    fun reloadAll() {
        configs.keys.forEach { configClass ->
            try {
                @Suppress("UNCHECKED_CAST")
                reload(configClass as KClass<Any>)
            } catch (e: Exception) {
                System.err.println("Failed to reload ${configClass.simpleName} during reloadAll(): ${e.message}")
            }
        }
    }

    /**
     * Save all loaded configurations.
     */
    fun saveAll() {
        configs.values.forEach { config ->
            try {
                save(config)
            } catch (e: Exception) {
                System.err.println("Failed to save ${config::class.simpleName} during saveAll(): ${e.message}")
            }
        }
    }

    /**
     * Check if a configuration is loaded.
     */
    fun isLoaded(configClass: KClass<*>): Boolean {
        return configs.containsKey(configClass)
    }

    /**
     * Get all loaded configuration classes.
     */
    fun getLoadedConfigs(): Set<KClass<*>> {
        return configs.keys.toSet()
    }

    /**
     * Clear all cached configurations (useful for testing).
     */
    fun clearCache() {
        configs.clear()
    }
}

/**
 * Exception thrown when configuration operations fail.
 */
class ConfigException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
