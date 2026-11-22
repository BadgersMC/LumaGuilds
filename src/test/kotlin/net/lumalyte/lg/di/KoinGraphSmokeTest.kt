package net.lumalyte.lg.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.lumalyte.lg.application.services.DurableInviteBlockService
import net.lumalyte.lg.application.services.InviteBlockService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.instance.ResolutionContext
import org.koin.core.parameter.ParametersHolder
import org.mockbukkit.mockbukkit.MockBukkit
import java.nio.file.Path
import kotlin.test.assertSame

/** Historical graph parity oracle. NexusGraphSmokeTest exercises the production graph. */
@OptIn(KoinInternalApi::class)
internal class KoinGraphSmokeTest {

    @TempDir lateinit var tempDir: Path
    private val testScope = CoroutineScope(Dispatchers.IO)

    @BeforeEach
    fun setUp() {
        MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `every definition in the real koin graph resolves`(claimsEnabled: Boolean) {
        withMigratedGraphDatabase(tempDir, claimsEnabled) { plugin, storage ->
            try {
                startKoin {
                    modules(appModule(plugin, storage, testScope, claimsEnabled))
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
                assertSame<Any>(
                    koin.get<InviteBlockService>(),
                    koin.get<DurableInviteBlockService>(),
                    "V1 and V2 must share the same cache and irreversible lifecycle gate",
                )
            } finally {
                testScope.cancel()
                stopKoin()
            }
        }
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
