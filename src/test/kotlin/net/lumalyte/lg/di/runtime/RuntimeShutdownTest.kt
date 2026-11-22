package net.lumalyte.lg.di.runtime

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REQ-NEXUS-CUTOVER-3: cleanup runs once, in stage order, and tolerates individual failures. */
class RuntimeShutdownTest {
    private val events = mutableListOf<String>()
    private val failures = mutableListOf<String>()
    private val shutdown = RuntimeShutdown { name, _ -> failures += name }

    @Test
    fun `stages close in contract order regardless of registration order`() {
        shutdown.register(ShutdownStage.STORAGE, "storage") { events += "storage" }
        shutdown.register(ShutdownStage.CONTEXT, "context") { events += "context" }
        shutdown.register(ShutdownStage.FLUSH, "xp flush") { events += "flush" }
        shutdown.register(ShutdownStage.TASKS, "timers") { events += "tasks" }
        shutdown.register(ShutdownStage.INTEGRATIONS, "papi") { events += "integrations" }
        shutdown.register(ShutdownStage.SERVICES, "services") { events += "services" }
        shutdown.register(ShutdownStage.GATES, "invite gate") { events += "gates" }

        assertTrue(shutdown.close())

        assertEquals(
            listOf("gates", "services", "integrations", "tasks", "flush", "context", "storage"),
            events,
        )
    }

    @Test
    fun `actions within one stage release in reverse acquisition order`() {
        shutdown.register(ShutdownStage.TASKS, "first") { events += "first" }
        shutdown.register(ShutdownStage.TASKS, "second") { events += "second" }

        shutdown.close()

        assertEquals(listOf("second", "first"), events)
    }

    @Test
    fun `a failing action does not prevent later stages from closing storage`() {
        shutdown.register(ShutdownStage.FLUSH, "broken flush") { error("disk full") }
        shutdown.register(ShutdownStage.CONTEXT, "context") { events += "context" }
        shutdown.register(ShutdownStage.STORAGE, "storage") { events += "storage" }

        shutdown.close()

        assertEquals(listOf("context", "storage"), events)
        assertEquals(listOf("broken flush"), failures)
    }

    @Test
    fun `linkage errors from optional plugins are contained`() {
        shutdown.register(ShutdownStage.INTEGRATIONS, "litebans") { throw NoClassDefFoundError("litebans/api/Events") }
        shutdown.register(ShutdownStage.STORAGE, "storage") { events += "storage" }

        shutdown.close()

        assertEquals(listOf("storage"), events)
        assertEquals(listOf("litebans"), failures)
    }

    @Test
    fun `repeated close is a no-op and every action runs exactly once`() {
        var storageCloses = 0
        shutdown.register(ShutdownStage.STORAGE, "storage") { storageCloses++ }

        assertTrue(shutdown.close())
        assertFalse(shutdown.close())

        assertEquals(1, storageCloses)
        assertTrue(shutdown.isClosed)
    }

    @Test
    fun `resources acquired after shutdown are released immediately instead of leaking`() {
        shutdown.close()
        var released = 0

        shutdown.register(ShutdownStage.INTEGRATIONS, "late RoseChat hook") { released++ }

        assertEquals(1, released)
        shutdown.close()
        assertEquals(1, released)
    }
}
