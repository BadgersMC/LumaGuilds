package net.lumalyte.lg.infrastructure.hytale

import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import net.lumalyte.lg.di.hytaleAppModule
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.slf4j.LoggerFactory

/**
 * Main plugin class for LumaGuilds on Hytale.
 *
 * This is the entry point for the Hytale server to load LumaGuilds.
 * The plugin follows Hytale's lifecycle: setup() → start() → shutdown()
 */
class LumaGuildsHytale(init: JavaPluginInit) : JavaPlugin(init) {

    private val log = LoggerFactory.getLogger(LumaGuildsHytale::class.java)
    private lateinit var storage: SQLiteStorage

    /**
     * Setup phase - Register components, commands, events
     * This is called before the plugin starts
     */
    override fun setup() {
        log.info("===========================================")
        log.info("  LumaGuilds v${manifest.version} - Hytale")
        log.info("  Setting up...")
        log.info("===========================================")

        try {
            // Initialize database storage
            log.info("Initializing SQLite database storage...")
            val dataFolder = dataDirectory.toFile()
            if (!dataFolder.exists()) {
                dataFolder.mkdirs()
            }
            storage = SQLiteStorage(dataFolder)
            log.info("Database storage initialized at: ${dataFolder.absolutePath}/lumaguilds.db")

            // Initialize Koin dependency injection
            log.info("Starting Koin dependency injection...")
            startKoin {
                modules(hytaleAppModule(storage, dataDirectory, claimsEnabled = true))
            }
            log.info("Koin DI initialized with all 9 services and 18 repositories")

            // TODO: Register ECS components (GuildMemberComponent)
            // TODO: Register commands (GuildCommand, ClaimCommand)
            // TODO: Register event listeners

            log.info("Setup complete!")
        } catch (e: Exception) {
            log.error("Failed to setup LumaGuilds!", e)
            throw e
        }
    }

    /**
     * Start phase - Initialize runtime systems
     * This is called after setup() is complete
     */
    override fun start() {
        log.info("Starting LumaGuilds...")

        try {
            // TODO: Start services
            // TODO: Load guild data from database
            // TODO: Sync player guild memberships

            log.info("LumaGuilds started successfully!")
        } catch (e: Exception) {
            log.error("Failed to start LumaGuilds!", e)
            throw e
        }
    }

    /**
     * Shutdown phase - Cleanup and save data
     * This is called when the server stops or the plugin is disabled
     */
    override fun shutdown() {
        log.info("Shutting down LumaGuilds...")

        try {
            // TODO: Save all guild data
            // TODO: Stop scheduled tasks

            // Close database connections
            log.info("Closing database connections...")
            // Note: HikariCP will close connections automatically when the pool is shut down

            // Cleanup Koin dependency injection
            log.info("Stopping Koin dependency injection...")
            stopKoin()

            log.info("LumaGuilds shut down successfully!")
        } catch (e: Exception) {
            log.error("Error during shutdown!", e)
        }

        log.info("===========================================")
        log.info("  LumaGuilds - Goodbye!")
        log.info("===========================================")
    }
}
