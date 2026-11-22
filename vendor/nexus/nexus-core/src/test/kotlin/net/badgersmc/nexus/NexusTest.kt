package net.badgersmc.nexus

import net.badgersmc.nexus.annotations.*
import net.badgersmc.nexus.config.ConfigManager
import net.badgersmc.nexus.config.TestConfig
import net.badgersmc.nexus.core.BeanDefinition
import net.badgersmc.nexus.core.NexusContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NexusTest {

    @Test
    fun `should create context and resolve singleton beans`() {
        val context = NexusContext.create(
            "net.badgersmc.nexus",
            this::class.java.classLoader
        )

        val service = context.getBean<TestService>()
        assertNotNull(service)

        // Singleton should return same instance
        val service2 = context.getBean<TestService>()
        assertTrue(service === service2)

        context.close()
    }

    @Test
    fun `should inject dependencies via constructor`() {
        val context = NexusContext.create(
            "net.badgersmc.nexus",
            this::class.java.classLoader
        )

        val service = context.getBean<TestService>()
        assertNotNull(service.repository)

        context.close()
    }

    @Test
    fun `should invoke post construct`() {
        val context = NexusContext.create(
            "net.badgersmc.nexus",
            this::class.java.classLoader
        )

        val service = context.getBean<TestService>()
        assertTrue(service.initialized)

        context.close()
    }

    @Test
    fun `should support manual bean registration`() {
        val context = NexusContext.create()

        val repo = TestRepository()
        context.registerBean("testRepository", TestRepository::class, repo)

        val retrieved = context.getBean<TestRepository>()
        assertEquals(repo, retrieved)

        context.close()
    }

    @Test
    fun `should throw when bean not found`() {
        val context = NexusContext.create()

        assertThrows<Exception> {
            context.getBean<TestService>()
        }

        context.close()
    }

    @Test
    fun `should not discover classes outside base package`() {
        val context = NexusContext.create(
            "net.badgersmc.nexus.nonexistent",
            this::class.java.classLoader
        )

        // No components in a non-existent package
        assertThrows<Exception> {
            context.getBean<TestService>()
        }

        context.close()
    }

    @Test
    fun `should support manual beans alongside scanned beans`() {
        val context = NexusContext.create(
            "net.badgersmc.nexus",
            this::class.java.classLoader
        )

        // Register a manual bean
        context.registerBean("manualValue", String::class, "hello")

        // Both scanned and manual beans should be accessible
        val service = context.getBean<TestService>()
        assertNotNull(service)
        assertEquals("hello", context.getBean(String::class))

        context.close()
    }

    @Test
    fun `should only discover annotated classes`() {
        val context = NexusContext.create(
            "net.badgersmc.nexus",
            this::class.java.classLoader
        )

        // BeanDefinition is in the scanned package but has no annotation — should not be discovered
        assertThrows<Exception> {
            context.getBean(BeanDefinition::class)
        }

        context.close()
    }

    @Test
    fun `should use default bean name from class name`() {
        val context = NexusContext.create(
            "net.badgersmc.nexus",
            this::class.java.classLoader
        )

        // TestRepository uses @Repository with no explicit name, so name defaults to "testRepository"
        assertTrue(context.containsBean("testRepository"))

        context.close()
    }

    @Test
    fun `should auto-discover and register ConfigFile classes`(@TempDir tempDir: Path) {
        val context = NexusContext.create(
            basePackage = "net.badgersmc.nexus.config",
            classLoader = this::class.java.classLoader,
            configDirectory = tempDir,
            contextName = "config-test"
        )

        // TestConfig has @ConfigFile("test") and lives in net.badgersmc.nexus.config
        val config = context.getBean(TestConfig::class)
        assertNotNull(config)
        assertEquals("Test Server", config.serverName)

        // ConfigManager should also be injectable
        val manager = context.getBean(ConfigManager::class)
        assertNotNull(manager)

        // Config file should have been created with defaults
        val configFile = tempDir.resolve("test.yaml").toFile()
        assertTrue(configFile.exists())

        context.close()
    }

    @Test
    fun `should support config beans alongside component beans`(@TempDir tempDir: Path) {
        val context = NexusContext.create(
            basePackage = "net.badgersmc.nexus",
            classLoader = this::class.java.classLoader,
            configDirectory = tempDir,
            contextName = "mixed-test"
        )

        // Both config and component beans should be resolvable
        val config = context.getBean(TestConfig::class)
        assertNotNull(config)

        val service = context.getBean<TestService>()
        assertNotNull(service)

        context.close()
    }
}

@Service
class TestService(val repository: TestRepository) {
    var initialized = false

    @PostConstruct
    fun init() {
        initialized = true
    }

    @PreDestroy
    fun cleanup() {
        // Cleanup logic
    }
}

@Repository
class TestRepository {
    fun getData(): String = "test data"
}
