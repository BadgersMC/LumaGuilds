package net.lumalyte.lg.infrastructure.services

import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AsyncTaskServiceTest {
    @Test
    fun `async tasks use injected dispatcher and preserve success and failure results`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val service = AsyncTaskService(scope, Dispatchers.IO, mockk<Plugin>())
            assertEquals("ok", service.runAsync { "ok" }.get(5, TimeUnit.SECONDS))
            val failed = service.runAsync<Int> { error("expected") }
            assertFailsWith<ExecutionException> { failed.get(5, TimeUnit.SECONDS) }
            assertEquals(7, runBlocking { service.runSuspend { 7 } })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a cancelled owning scope does not strand new futures`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val service = AsyncTaskService(scope, Dispatchers.Default, mockk<Plugin>())
        scope.cancel()
        val cancelled = service.runAsync { 123 }
        assertFailsWith<java.util.concurrent.CancellationException> {
            cancelled.get(5, TimeUnit.SECONDS)
        }
    }
}
