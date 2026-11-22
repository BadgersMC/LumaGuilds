package net.lumalyte.lg.di.runtime

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.lumalyte.lg.LumaGuilds
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitScheduler
import kotlin.test.Test
import kotlin.test.assertFailsWith

class FailedIntegrationStartupTest {
    @Test fun failureBeforeScheduleStartupStillCancelsPluginTasks() {
        val scheduler = mockk<BukkitScheduler>(relaxed = true)
        val plugin = mockk<LumaGuilds>(relaxed = true) {
            every { server.scheduler } returns scheduler
        }
        val shutdown = RuntimeShutdown { _, failure -> throw failure }
        mockkStatic(Bukkit::class)
        try {
            every { Bukkit.getPluginManager() } throws IllegalStateException("registration failed")
            val steps = PaperGuildRuntime(plugin, false, mockk())
            assertFailsWith<IllegalStateException> { steps.registerIntegrations(mockk(relaxed = true), shutdown) }
            shutdown.close()
            shutdown.close()
            verify(exactly = 1) { scheduler.cancelTasks(plugin) }
        } finally {
            unmockkStatic(Bukkit::class)
        }
    }
}
