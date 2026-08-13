package net.lumalyte.lg.di

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.infrastructure.persistence.migrations.SQLiteMigrations
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.instance.ResolutionContext
import org.koin.core.parameter.ParametersHolder
import org.mockbukkit.mockbukkit.MockBukkit
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger

/**
 * Boot smoke test: composes the REAL Koin graph — the same `appModule` the server
 * builds — and force-constructs every registered definition.
 *
 * Guards against the v2.1.23 failure mode: a module referenced a type
 * (`GetClaimAtPosition`) that had no registration. The plugin enabled, cleaned the
 * database, then crashed with `NoDefinitionFoundException` mid-boot. A class-presence
 * check in the release workflow would have PASSED the broken jar (the class file was
 * there; what was missing was the Koin registration). This test catches the real
 * failure: it resolves every definition eagerly, so any missing registration throws
 * `InstanceNotFoundException` (Koin 4) immediately.
 *
 * Uses MockBukkit for a realistic Bukkit environment and a real in-memory SQLite
 * storage so the graph is as close to production as possible.
 *
 * The instance registry iteration uses `@KoinInternalApi` surfaces (InstanceRegistry,
 * InstanceFactory) — deliberately, since the whole point is to enumerate every
 * registered definition regardless of what the public `getAll` overload exposes.
 */
@OptIn(KoinInternalApi::class)
internal class KoinGraphSmokeTest {

    private lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        MockBukkit.mock()
        tempDir = Files.createTempDirectory("lg-graph-test")
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
        MockBukkit.unmock()
    }

    @Test
    fun `every definition in the real koin graph resolves`() {
        val plugin = mockk<LumaGuilds>(relaxed = true)
        every { plugin.dataFolder } returns tempDir.toFile()
        every { plugin.config } returns YamlConfiguration()
        every { plugin.logger } returns Logger.getLogger("LumaGuilds-GraphTest")
        every { plugin.pluginScope } returns CoroutineScope(Dispatchers.IO)

        val storage = SQLiteStorage(tempDir.toFile())

        // Run DB migrations before composing the graph (same order as production
        // onEnable: initDatabase → startKoin). The repos preload data at construction
        // time and need the schema to exist.
        every { plugin.getComponentLogger() } returns ComponentLogger.logger("LumaGuilds-GraphTest")
        storage.connection.getConnection().use { conn ->
            SQLiteMigrations(plugin, conn, claimsEnabled = true).migrate()
        }

        startKoin {
            modules(appModule(plugin, storage, claimsEnabled = true))
        }

        val koin = GlobalContext.get()
        val rootScope = koin.scopeRegistry.rootScope
        val factories = koin.instanceRegistry.instances.values.toList()

        assertTrue(factories.isNotEmpty(), "Koin graph registered zero definitions")

        // Force-construct every registered definition by calling its factory.
        // A definition whose constructor references an unregistered type throws
        // InstanceNotFoundException (Koin 4) / NoDefinitionFoundException (Koin 3).
        val failures = mutableListOf<String>()
        for (factory in factories) {
            val bean = factory.beanDefinition
            val context = ResolutionContext(
                logger = koin.logger,
                scope = rootScope,
                clazz = bean.primaryType,
                qualifier = bean.qualifier,
                parameters = ParametersHolder()
            )
            try {
                factory.get(context)
            } catch (e: Throwable) {
                val name = bean.primaryType.simpleName ?: bean.primaryType.toString()
                failures.add("$name: ${e::class.simpleName} — ${causeChain(e)}")
            }
        }

        assertTrue(
            failures.isEmpty(),
            "Koin graph has ${failures.size} unresolvable definition(s):\n" +
                failures.joinToString("\n")
        )
    }

    /** Flattens the root-cause chain into a readable one-liner. */
    private fun causeChain(t: Throwable): String {
        val parts = mutableListOf<String>()
        var current: Throwable? = t
        var depth = 0
        while (current != null && depth < 6) {
            val msg = current.message?.replace('\n', ' ')?.trim()
            if (!msg.isNullOrEmpty()) {
                parts.add("${current::class.simpleName}: $msg")
            }
            current = current.cause
            depth++
        }
        return parts.joinToString(" ← ")
    }
}