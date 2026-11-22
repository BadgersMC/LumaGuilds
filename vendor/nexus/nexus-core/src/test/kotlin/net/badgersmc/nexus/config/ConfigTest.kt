package net.badgersmc.nexus.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConfigTest {

    @Test
    fun `should create config file with default values`(@TempDir tempDir: Path) {
        val loader = ConfigLoader(tempDir)
        val config = loader.load(TestConfig::class)

        assertNotNull(config)
        assertEquals("Test Server", config.serverName)
        assertEquals(100, config.maxPlayers)
        assertTrue(config.enablePvp)

        // Verify file was created
        val configFile = tempDir.resolve("test.yaml").toFile()
        assertTrue(configFile.exists())
    }

    @Test
    fun `should save and load config values`(@TempDir tempDir: Path) {
        val loader = ConfigLoader(tempDir)

        // Create and modify config
        val config = loader.load(TestConfig::class)
        config.serverName = "My Custom Server"
        config.maxPlayers = 200
        config.enablePvp = false

        // Save
        loader.save(config)

        // Load fresh instance
        val reloaded = loader.load(TestConfig::class)

        assertEquals("My Custom Server", reloaded.serverName)
        assertEquals(200, reloaded.maxPlayers)
        assertEquals(false, reloaded.enablePvp)
    }

    @Test
    fun `should support nested config objects`(@TempDir tempDir: Path) {
        val loader = ConfigLoader(tempDir)

        val config = loader.load(AdvancedConfig::class)
        config.database.host = "localhost"
        config.database.port = 5432

        loader.save(config)

        val reloaded = loader.load(AdvancedConfig::class)
        assertEquals("localhost", reloaded.database.host)
        assertEquals(5432, reloaded.database.port)
    }

    @Test
    fun `should respect config name annotations`(@TempDir tempDir: Path) {
        val loader = ConfigLoader(tempDir)
        val config = loader.load(TestConfig::class)
        config.maxPlayers = 500

        loader.save(config)

        // Check file content contains the custom name
        val content = tempDir.resolve("test.yaml").toFile().readText()
        assertTrue(content.contains("max-players"))
    }

    @Test
    fun `should use ConfigManager for centralized management`(@TempDir tempDir: Path) {
        val manager = ConfigManager(tempDir)

        // Load configs
        val testConfig = manager.load<TestConfig>()
        val advancedConfig = manager.load<AdvancedConfig>()

        assertNotNull(testConfig)
        assertNotNull(advancedConfig)

        // Modify and save
        testConfig.serverName = "Managed Server"
        manager.save(testConfig)

        // Reload
        manager.reload<TestConfig>()

        val retrieved = manager.get<TestConfig>()
        assertEquals("Managed Server", retrieved.serverName)
    }

    @Test
    fun `should reload all configs`(@TempDir tempDir: Path) {
        val manager = ConfigManager(tempDir)

        manager.load<TestConfig>()
        manager.load<AdvancedConfig>()

        assertEquals(2, manager.getLoadedConfigs().size)

        // Should not throw
        manager.reloadAll()
    }

    @Test
    fun `should skip transient fields`(@TempDir tempDir: Path) {
        val loader = ConfigLoader(tempDir)
        val config = loader.load(TestConfig::class)

        config.runtimeCache = "Should not be saved"
        loader.save(config)

        val reloaded = loader.load(TestConfig::class)
        assertEquals(null, reloaded.runtimeCache) // Should be null, not saved
    }
}

@ConfigFile("test")
@Comment("Test configuration file", "This demonstrates the config system")
class TestConfig {
    @Comment("The name of the server")
    @ConfigName("server-name")
    var serverName: String = "Test Server"

    @Comment("Maximum players allowed", "Set to -1 for unlimited")
    @ConfigName("max-players")
    var maxPlayers: Int = 100

    @Comment("Enable PvP combat")
    var enablePvp: Boolean = true

    var allowedCommands: List<String> = listOf("help", "spawn", "home")

    @Transient
    var runtimeCache: String? = null
}

@ConfigFile("advanced")
class AdvancedConfig {
    var database: DatabaseConfig = DatabaseConfig()

    class DatabaseConfig {
        var host: String = "127.0.0.1"
        var port: Int = 3306
        var username: String = "root"
        var password: String = ""
    }
}
