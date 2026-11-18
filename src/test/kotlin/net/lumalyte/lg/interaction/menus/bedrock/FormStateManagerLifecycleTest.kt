package net.lumalyte.lg.interaction.menus.bedrock

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Lifecycle contract for the Bedrock form state cleanup child job.
 * FormStateManager is a process-wide singleton, so every test resets it.
 */
class FormStateManagerLifecycleTest {

    private lateinit var ownerScope: CoroutineScope

    @BeforeEach
    fun setUp() {
        FormStateManager.shutdown()
        ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @AfterEach
    fun tearDown() {
        FormStateManager.shutdown()
        ownerScope.cancel()
    }

    @Test
    fun `does not schedule cleanup until explicitly started`() {
        assertFalse(FormStateManager.isCleanupActive())
    }

    @Test
    fun `start launches a single cleanup child of the owner scope and is idempotent`() {
        assertTrue(FormStateManager.start(ownerScope, Dispatchers.Default))
        assertFalse(FormStateManager.start(ownerScope, Dispatchers.Default))

        assertTrue(FormStateManager.isCleanupActive())
        assertEquals(1, ownerScope.coroutineContext[kotlinx.coroutines.Job]!!.children.count())
    }

    @Test
    fun `shutdown cancels only the cleanup child and never the owner scope`() {
        FormStateManager.start(ownerScope, Dispatchers.Default)

        FormStateManager.shutdown()

        assertFalse(FormStateManager.isCleanupActive())
        assertTrue(ownerScope.isActive)
        assertEquals(0, ownerScope.coroutineContext[kotlinx.coroutines.Job]!!.children.count())
    }

    @Test
    fun `shutdown clears state and is safe to repeat`() {
        FormStateManager.start(ownerScope, Dispatchers.Default)
        FormStateManager.saveState("player:menu:step", mapOf("a" to 1))

        FormStateManager.shutdown()
        FormStateManager.shutdown()

        assertEquals(0, FormStateManager.getActiveStateCount())
    }

    @Test
    fun `restarts cleanly after shutdown, including on a new owner scope`() {
        FormStateManager.start(ownerScope, Dispatchers.Default)
        FormStateManager.shutdown()

        // Simulates a plugin reload: the previous owner scope is gone, a fresh one is supplied.
        ownerScope.cancel()
        ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        assertTrue(FormStateManager.start(ownerScope, Dispatchers.Default))
        assertTrue(FormStateManager.isCleanupActive())
    }

    @Test
    fun `start relaunches if the previous owner scope was cancelled externally`() {
        FormStateManager.start(ownerScope, Dispatchers.Default)
        ownerScope.cancel()
        ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        assertTrue(FormStateManager.start(ownerScope, Dispatchers.Default))
        assertTrue(FormStateManager.isCleanupActive())
    }

    @Test
    fun `unexpired state is retained and restored`() {
        FormStateManager.saveState("player:menu:step", mapOf("a" to 1))

        FormStateManager.cleanupExpiredStates()

        assertEquals(mapOf("a" to 1), FormStateManager.restoreState("player:menu:step"))
        assertNotNull(FormStateManager.getStateMetadata("player:menu:step"))
    }

    @Test
    fun `expired state is lazily removed on restore`() {
        FormStateManager.saveState("player:menu:step", mapOf("a" to 1), expirationMinutes = -1)

        assertNull(FormStateManager.restoreState("player:menu:step"))
        assertEquals(0, FormStateManager.getActiveStateCount())
    }

    @Test
    fun `cleanup sweep removes only expired states`() {
        FormStateManager.saveState("player:menu:old", mapOf("a" to 1), expirationMinutes = -1)
        FormStateManager.saveState("player:menu:new", mapOf("b" to 2))

        FormStateManager.cleanupExpiredStates()

        assertEquals(listOf("player:menu:new"), FormStateManager.getPlayerStateKeys("player"))
    }

    @Test
    fun `running cleanup child periodically sweeps expired states`() {
        FormStateManager.saveState("player:menu:old", mapOf("a" to 1), expirationMinutes = -1)
        FormStateManager.saveState("player:menu:new", mapOf("b" to 2))

        // Short test-only interval instead of the production 10-minute cadence.
        FormStateManager.start(ownerScope, Dispatchers.Default, cleanupIntervalMillis = 10)

        val deadline = System.currentTimeMillis() + 5_000
        while (FormStateManager.getActiveStateCount() > 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertEquals(listOf("player:menu:new"), FormStateManager.getPlayerStateKeys("player"))
    }

    @Test
    fun `no independent executor and live startup wires the plugin scope and virtual dispatcher`() {
        val manager = java.io.File(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock/FormStateManager.kt"
        ).readText()
        assertFalse(manager.contains("Executors."), "FormStateManager must not allocate its own executor")
        assertFalse(manager.contains("ScheduledExecutorService"))

        val plugin = java.io.File("src/main/kotlin/net/lumalyte/lg/LumaGuilds.kt").readText()
        val koinStart = plugin.indexOf("startKoin {")
        val formStart = plugin.indexOf("FormStateManager.start(")
        assertTrue(koinStart >= 0 && formStart > koinStart, "FormStateManager must start after Koin")
        val call = plugin.substring(formStart, minOf(plugin.length, formStart + 300))
        assertTrue(call.contains("pluginScope"))
        assertTrue(call.contains("named(\"VirtualDispatcher\")"))
    }

    @Test
    fun `production cadence and expiration defaults are preserved`() {
        assertEquals(10L * 60_000L, FormStateManager.CLEANUP_INTERVAL_MILLIS)
        assertEquals(30L, FormStateManager.DEFAULT_EXPIRATION_MINUTES)
    }
}
