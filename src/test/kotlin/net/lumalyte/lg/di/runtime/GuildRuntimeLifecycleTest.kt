package net.lumalyte.lg.di.runtime

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * REQ-NEXUS-CUTOVER-2/3: one runtime owner activates in strict order and releases exactly the
 * resources it acquired, once, on failed startup or disable.
 */
class GuildRuntimeLifecycleTest {
    private class Storage
    private class Context

    private class RecordingSteps(private val failAt: String? = null) : GuildRuntimeSteps<Storage, Context> {
        val events = mutableListOf<String>()
        val storage = Storage()
        val context = Context()
        var activations = 0

        private fun step(name: String) {
            events += name
            if (name == failAt) throw IllegalStateException("$name failed")
        }

        override fun verifyCommandParity() = step("parity")
        override fun openStorage(): Storage { step("openStorage"); return storage }
        override fun migrate(storage: Storage) = step("migrate")
        override fun closeStorage(storage: Storage) { events += "closeStorage" }
        override fun activate(storage: Storage): Context {
            assertSame(this.storage, storage)
            activations++
            step("activate")
            return context
        }
        override fun closeContext(context: Context) { events += "closeContext" }

        override fun publishServices(context: Context, shutdown: RuntimeShutdown) {
            step("publishServices")
            shutdown.register(ShutdownStage.SERVICES, "services") { events += "unpublishServices" }
        }

        override fun registerIntegrations(context: Context, shutdown: RuntimeShutdown) {
            step("registerIntegrations")
            shutdown.register(ShutdownStage.INTEGRATIONS, "placeholders") { events += "unregisterIntegrations" }
        }

        override fun registerListeners(context: Context, shutdown: RuntimeShutdown) {
            step("registerListeners")
            shutdown.register(ShutdownStage.TASKS, "listeners") { events += "unregisterListeners" }
        }

        override fun registerCommands(context: Context, shutdown: RuntimeShutdown) = step("registerCommands")

        override fun startSchedules(context: Context, shutdown: RuntimeShutdown) {
            step("startSchedules")
            shutdown.register(ShutdownStage.TASKS, "schedules") { events += "stopSchedules" }
            shutdown.register(ShutdownStage.FLUSH, "xp") { events += "flushXp" }
        }
    }

    private val startup = listOf(
        "parity", "openStorage", "migrate", "activate", "publishServices",
        "registerIntegrations", "registerListeners", "registerCommands", "startSchedules",
    )

    @Test
    fun `startup activates one context after migrations and before any registration`() {
        val steps = RecordingSteps()
        val lifecycle = GuildRuntimeLifecycle(steps) { _, _ -> }

        assertSame(steps.context, lifecycle.start())

        assertEquals(startup, steps.events)
        assertEquals(1, steps.activations)
        assertSame(steps.context, lifecycle.context)
    }

    @Test
    fun `placeholder integrations register only after the graph is active and services are published`() {
        val steps = RecordingSteps()
        GuildRuntimeLifecycle(steps) { _, _ -> }.start()

        val integrations = steps.events.indexOf("registerIntegrations")
        assertTrue(integrations > steps.events.indexOf("activate"))
        assertTrue(integrations > steps.events.indexOf("publishServices"))
    }

    @Test
    fun `disable stops tasks, flushes, then closes context and finally storage`() {
        val steps = RecordingSteps()
        val lifecycle = GuildRuntimeLifecycle(steps) { _, _ -> }
        lifecycle.start()
        steps.events.clear()

        assertTrue(lifecycle.stop())

        assertEquals(
            listOf(
                "unpublishServices", "unregisterIntegrations", "stopSchedules", "unregisterListeners",
                "flushXp", "closeContext", "closeStorage",
            ),
            steps.events,
        )
        assertNull(lifecycle.context)
    }

    @Test
    fun `repeated disable closes context and storage exactly once`() {
        val steps = RecordingSteps()
        val lifecycle = GuildRuntimeLifecycle(steps) { _, _ -> }
        lifecycle.start()

        assertTrue(lifecycle.stop())
        assertFalse(lifecycle.stop())

        assertEquals(1, steps.events.count { it == "closeContext" })
        assertEquals(1, steps.events.count { it == "closeStorage" })
    }

    @Test
    fun `unproven command parity blocks startup before storage or context exist`() {
        val steps = RecordingSteps(failAt = "parity")
        val lifecycle = GuildRuntimeLifecycle(steps) { _, _ -> }

        assertThrows<IllegalStateException> { lifecycle.start() }

        assertEquals(listOf("parity"), steps.events)
        assertEquals(0, steps.activations)
    }

    @Test
    fun `storage open failure leaves nothing to close`() {
        val steps = RecordingSteps(failAt = "openStorage")

        assertThrows<IllegalStateException> { GuildRuntimeLifecycle(steps) { _, _ -> }.start() }

        assertEquals(listOf("parity", "openStorage"), steps.events)
    }

    @Test
    fun `migration failure closes the opened storage once and never activates Nexus`() {
        val steps = RecordingSteps(failAt = "migrate")
        val lifecycle = GuildRuntimeLifecycle(steps) { _, _ -> }

        assertThrows<IllegalStateException> { lifecycle.start() }
        lifecycle.stop()

        assertEquals(listOf("parity", "openStorage", "migrate", "closeStorage"), steps.events)
        assertEquals(0, steps.activations)
    }

    @ParameterizedTest
    @ValueSource(strings = ["activate", "publishServices", "registerIntegrations", "registerListeners", "registerCommands", "startSchedules"])
    fun `failure after storage releases only what was acquired, context before storage, once`(failAt: String) {
        val steps = RecordingSteps(failAt = failAt)
        val lifecycle = GuildRuntimeLifecycle(steps) { _, _ -> }

        assertThrows<IllegalStateException> { lifecycle.start() }
        assertFalse(lifecycle.stop(), "Bukkit's onDisable after a failed enable must not clean up twice")

        val cleanup = steps.events.drop(steps.events.indexOf(failAt) + 1)
        assertEquals(1, cleanup.count { it == "closeStorage" })
        assertEquals("closeStorage", cleanup.last())
        val contextActivated = failAt != "activate"
        assertEquals(if (contextActivated) 1 else 0, cleanup.count { it == "closeContext" })
        if (contextActivated) assertEquals(cleanup.size - 2, cleanup.indexOf("closeContext"))
        val reached = startup.indexOf(failAt)
        assertEquals(reached > startup.indexOf("publishServices"), "unpublishServices" in cleanup)
        assertEquals(reached > startup.indexOf("registerIntegrations"), "unregisterIntegrations" in cleanup)
        assertFalse("stopSchedules" in cleanup, "schedules never started")
        assertFalse("startSchedules" in steps.events && failAt != "startSchedules")
        assertNull(lifecycle.context)
    }

    @Test
    fun `failure during cleanup is reported and storage still closes`() {
        val failures = mutableListOf<String>()
        val steps = object : GuildRuntimeSteps<Storage, Context> by RecordingSteps() {
            var storageClosed = 0
            override fun closeContext(context: Context) = error("scope stuck")
            override fun closeStorage(storage: Storage) { storageClosed++ }
        }
        val lifecycle = GuildRuntimeLifecycle(steps) { name, _ -> failures += name }
        lifecycle.start()

        lifecycle.stop()

        assertEquals(1, steps.storageClosed)
        assertEquals(listOf("Nexus context"), failures)
    }

    @Test
    fun `a lifecycle cannot activate a second context`() {
        val steps = RecordingSteps()
        val lifecycle = GuildRuntimeLifecycle(steps) { _, _ -> }
        lifecycle.start()

        assertThrows<IllegalStateException> { lifecycle.start() }

        assertEquals(1, steps.activations)
    }
}
