package net.lumalyte.lg.acceptance

import io.mockk.*
import org.bukkit.Server
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.PluginManager
import org.junit.jupiter.api.Test
import java.util.logging.Logger

class ProbeDisabledStartupTest {
    @Test fun `disabled configuration stops before event registration or scheduling`() {
        val probe = mockk<GuildsAcceptanceProbe>(relaxed = true)
        val server = mockk<Server>(relaxed = true)
        val manager = mockk<PluginManager>(relaxed = true)
        every { probe.config } returns YamlConfiguration().apply { set("enabled", false) }
        every { probe.server } returns server
        every { server.pluginManager } returns manager
        every { probe.logger } returns Logger.getAnonymousLogger()
        every { probe.onEnable() } answers { callOriginal() }
        probe.onEnable()
        verify(exactly = 1) { manager.disablePlugin(probe) }
        verify(exactly = 0) { manager.registerEvents(any(), any()) }
        verify(exactly = 0) { server.scheduler }
    }
}
