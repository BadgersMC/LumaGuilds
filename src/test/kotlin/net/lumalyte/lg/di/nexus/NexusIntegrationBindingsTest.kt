package net.lumalyte.lg.di.nexus

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.badgersmc.nexus.core.NexusContext
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.infrastructure.placeholders.LumaGuildsExpansion
import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.PluginManager
import org.junit.jupiter.api.Test
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NexusIntegrationBindingsTest {

    private val allApolloNames = setOf(
        "lunarClientService",
        "guildTeamService", "guildTeamListener",
        "guildWaypointService",
        "guildNotificationService", "guildNotificationListener",
        "guildRichPresenceService", "guildRichPresenceListener",
    )

    private inline fun withContext(block: (NexusContext) -> Unit) {
        val context = NexusContext.create()
        try {
            block(context)
        } finally {
            context.close()
        }
    }

    /** Runs [block] with Bukkit's plugin manager reporting Apollo-Bukkit as installed or not. */
    private inline fun withApollo(installed: Boolean, block: (PluginManager) -> Unit) {
        val plugins = mockk<PluginManager>()
        every { plugins.getPlugin("Apollo-Bukkit") } returns if (installed) mockk<Plugin>() else null
        mockkStatic(Bukkit::class)
        try {
            every { Bukkit.getPluginManager() } returns plugins
            block(plugins)
        } finally {
            unmockkStatic(Bukkit::class)
        }
    }

    private fun pluginWith(config: FileConfiguration): LumaGuilds {
        val plugin = mockk<LumaGuilds>()
        every { plugin.config } returns config
        every { plugin.logger } returns Logger.getLogger("NexusIntegrationBindingsTest")
        return plugin
    }

    private fun yaml(vararg flags: Pair<String, Boolean>): YamlConfiguration =
        YamlConfiguration().apply { flags.forEach { (path, value) -> set(path, value) } }

    private fun registeredNames(context: NexusContext, plugin: LumaGuilds): Set<String> {
        val before = context.getBeanNames().toSet()
        context.registerIntegrationBindings(plugin)
        return context.getBeanNames().toSet() - before
    }

    @Test
    fun `without Apollo-Bukkit only the PlaceholderAPI expansion is registered`() = withContext { context ->
        withApollo(installed = false) { plugins ->
            // Strict config: Koin short-circuits before reading apollo.enabled when Apollo is absent.
            val config = mockk<FileConfiguration>()
            val plugin = mockk<LumaGuilds>()
            every { plugin.config } returns config

            assertEquals(setOf("lumaGuildsExpansion"), registeredNames(context, plugin))
            assertFalse(context.containsBean(GUILD_WAYPOINT_SERVICE_BEAN))
            assertNull(context.optionalGuildWaypointService())

            verify(exactly = 1) { plugins.getPlugin("Apollo-Bukkit") }
            confirmVerified(config)
        }
    }

    @Test
    fun `plugin manager failure is treated as Apollo not installed`() = withContext { context ->
        mockkStatic(Bukkit::class)
        try {
            every { Bukkit.getPluginManager() } throws IllegalStateException("server not ready")
            val config = mockk<FileConfiguration>()
            val plugin = mockk<LumaGuilds>()
            every { plugin.config } returns config

            assertEquals(setOf("lumaGuildsExpansion"), registeredNames(context, plugin))
            confirmVerified(config)
        } finally {
            unmockkStatic(Bukkit::class)
        }
    }

    @Test
    fun `apollo disabled in config registers only the expansion`() = withContext { context ->
        withApollo(installed = true) {
            val names = registeredNames(context, pluginWith(yaml("apollo.enabled" to false)))
            assertEquals(setOf("lumaGuildsExpansion"), names)
        }
    }

    @Test
    fun `Apollo installed with default flags registers every integration definition`() = withContext { context ->
        withApollo(installed = true) {
            val names = registeredNames(context, pluginWith(YamlConfiguration()))
            assertEquals(allApolloNames + "lumaGuildsExpansion", names)
            // Registered under the shared contract only; no concrete alias to make lookups ambiguous.
            assertFalse("lunarClientServiceBukkit" in names)
        }
    }

    @Test
    fun `each feature flag independently gates its service and listener`() {
        val cases = mapOf(
            "apollo.teams.enabled" to setOf("guildTeamService", "guildTeamListener"),
            "apollo.waypoints.enabled" to setOf("guildWaypointService"),
            "apollo.notifications.enabled" to setOf("guildNotificationService", "guildNotificationListener"),
            "apollo.richpresence.enabled" to setOf("guildRichPresenceService", "guildRichPresenceListener"),
        )
        cases.forEach { (flag, gated) ->
            withContext { context ->
                withApollo(installed = true) {
                    val names = registeredNames(context, pluginWith(yaml(flag to false)))
                    assertEquals(allApolloNames - gated + "lumaGuildsExpansion", names, "flag $flag")
                    assertTrue("lunarClientService" in names, "flag $flag keeps LunarClientService")
                }
            }
        }
    }

    @Test
    fun `waypoints disabled keeps the team listener with an absent optional waypoint service`() =
        withContext { context ->
            withApollo(installed = true) {
                val names = registeredNames(context, pluginWith(yaml("apollo.waypoints.enabled" to false)))
                assertTrue("guildTeamListener" in names)
                assertFalse(context.containsBean(GUILD_WAYPOINT_SERVICE_BEAN))
                assertNull(context.optionalGuildWaypointService())
            }
        }

    @Test
    fun `registration constructs nothing and resolves no collaborators`() = withContext { context ->
        withApollo(installed = true) {
            val plugin = pluginWith(YamlConfiguration())
            val guilds = mockk<GuildService>()
            val members = mockk<MemberService>()
            val ranks = mockk<RankService>()
            val pluginContract = mockk<Plugin>()
            context.registerBean("guildService", GuildService::class, guilds)
            context.registerBean("memberService", MemberService::class, members)
            context.registerBean("rankService", RankService::class, ranks)
            context.registerBean("pluginContract", Plugin::class, pluginContract)

            context.registerIntegrationBindings(plugin)

            // Strict mocks record any call a factory or constructor would make.
            confirmVerified(guilds, members, ranks, pluginContract)
        }
    }

    @Test
    fun `PlaceholderAPI expansion resolves lazily as a singleton`() = withContext { context ->
        withApollo(installed = false) {
            val plugin = mockk<LumaGuilds>()
            every { plugin.config } returns mockk()
            context.registerIntegrationBindings(plugin)

            val expansion = context.getBean<LumaGuildsExpansion>()
            assertSame(expansion, context.getBean<LumaGuildsExpansion>())
        }
    }
}
