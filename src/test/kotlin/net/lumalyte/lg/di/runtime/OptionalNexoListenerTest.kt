package net.lumalyte.lg.di.runtime

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.lumalyte.lg.utils.NexoItemProvider
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.PluginManager
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class OptionalNexoListenerTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `Paper inspects the Nexo event listener only when Nexo supplies its event class`(installed: Boolean) {
        val manager = mockk<PluginManager>(relaxed = true)
        val plugin = mockk<Plugin> { every { server.pluginManager } returns manager }
        every { manager.getPlugin("Nexo") } returns if (installed) mockk() else null
        mockkStatic(Bukkit::class)
        try {
            every { Bukkit.getPluginManager() } returns manager
            NexoItemProvider.register(plugin)
            verify(exactly = if (installed) 1 else 0) { manager.registerEvents(NexoItemProvider, plugin) }
        } finally {
            unmockkStatic(Bukkit::class)
        }
    }
}
