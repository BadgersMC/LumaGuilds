package net.lumalyte.lg.infrastructure.services

import io.github.badgersmc.advancements.pilot.ProjectionService
import io.mockk.*
import net.lumalyte.lg.domain.achievements.AchievementRecord
import org.bukkit.event.server.ServiceRegisterEvent
import org.bukkit.event.server.ServiceUnregisterEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.plugin.ServicePriority
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import kotlin.test.*

internal class GuildAchievementProjectionLifecycleTest {
    @AfterEach fun cleanup() { unmockkAll(); MockBukkit.unmock() }

    @Test fun `optional projection binds in either enable order and detaches stale providers`() {
        val server = MockBukkit.mock()
        val owner = MockBukkit.createMockPlugin("LumaGuilds")
        val companion = MockBukkit.createMockPlugin("EnthusiaAdvancements")
        val first = mockk<ProjectionService>(relaxed = true)
        val second = mockk<ProjectionService>(relaxed = true)
        val projection = GuildAchievementProjection(owner, emptyList())
        projection.register()
        assertFalse(projection.available)
        server.servicesManager.register(ProjectionService::class.java, first, companion, ServicePriority.Normal)
        val firstRegistration = server.servicesManager.getRegistration(ProjectionService::class.java)!!
        // Also deliver explicitly: implementations may publish during onEnable, before PluginEnableEvent.
        server.pluginManager.callEvent(ServiceRegisterEvent(firstRegistration))
        assertTrue(projection.available)
        projection.register()
        verify(exactly = 1) { first.registerTree(owner, "lumaguilds_mastery", any(), emptyList()) }
        server.servicesManager.unregister(ProjectionService::class.java, first)
        server.pluginManager.callEvent(ServiceUnregisterEvent(firstRegistration))
        assertFalse(projection.available)
        val player = server.addPlayer()
        clearMocks(first, answers = false)
        projection.project(player, AchievementRecord())
        verify { first wasNot Called }
        server.servicesManager.register(ProjectionService::class.java, second, companion, ServicePriority.Normal)
        server.pluginManager.callEvent(ServiceRegisterEvent(server.servicesManager.getRegistration(ProjectionService::class.java)!!))
        assertTrue(projection.available)
        verify(exactly = 1) { second.registerTree(owner, "lumaguilds_mastery", any(), emptyList()) }
        projection.close()
        assertFalse(projection.available)
        verify(exactly = 1) { second.removeTree(owner, "lumaguilds_mastery") }
        // Provider-first startup uses the same discovery and owns only Guilds' namespace.
        val early = GuildAchievementProjection(owner, emptyList())
        early.register()
        assertTrue(early.available)
        early.close()
    }

    @Test fun `descriptor does not recreate the KOTH Guilds Advancements dependency cycle`() {
        val descriptor = javaClass.classLoader.getResourceAsStream("plugin.yml")!!.bufferedReader().readText()
        val yaml = org.yaml.snakeyaml.Yaml().load<Map<String, Any>>(descriptor)
        val after = (yaml["depend"] as? List<*>).orEmpty() + (yaml["softdepend"] as? List<*>).orEmpty()
        assertFalse("EnthusiaAdvancements" in after, "KOTH already waits for Guilds and loads before Advancements")
    }

    @Test fun `provider replacement and disable never retain a stale projection`() {
        val server = MockBukkit.mock()
        val owner = MockBukkit.createMockPlugin("LumaGuilds")
        val companion = MockBukkit.createMockPlugin("EnthusiaAdvancements")
        val old = mockk<ProjectionService>(relaxed = true)
        val replacement = mockk<ProjectionService>(relaxed = true)
        server.servicesManager.register(ProjectionService::class.java, old, companion, ServicePriority.Low)
        val oldRegistration = server.servicesManager.getRegistration(ProjectionService::class.java)!!
        val projection = GuildAchievementProjection(owner, emptyList())
        projection.register()
        server.servicesManager.register(ProjectionService::class.java, replacement, companion, ServicePriority.High)
        server.pluginManager.callEvent(ServiceRegisterEvent(server.servicesManager.getRegistration(ProjectionService::class.java)!!))
        verify(exactly = 1) { old.removeTree(owner, "lumaguilds_mastery") }
        verify(exactly = 1) { replacement.registerTree(owner, "lumaguilds_mastery", any(), emptyList()) }
        server.servicesManager.unregister(ProjectionService::class.java, old)
        server.pluginManager.callEvent(ServiceUnregisterEvent(oldRegistration))
        assertTrue(projection.available, "Unregistering a superseded provider must not detach its replacement")
        server.pluginManager.callEvent(PluginDisableEvent(companion))
        assertFalse(projection.available)
        verify(exactly = 1) { replacement.removeTree(owner, "lumaguilds_mastery") }
    }
}

