package net.lumalyte.lg.di.nexus

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import net.badgersmc.nexus.core.NexusContext
import net.lumalyte.lg.application.services.DiscordAccountLinkSubscription
import net.lumalyte.lg.application.services.DiscordGuildProfileSubscription
import net.lumalyte.lg.infrastructure.services.UnavailableDiscordAccountLinkSubscription
import net.lumalyte.lg.infrastructure.services.UnavailableDiscordGuildProfileSubscription
import org.bukkit.Bukkit
import org.bukkit.plugin.PluginManager
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class NexusDiscordBindingsTest {
    @Test
    fun `Discord integrations register lazily without touching Bukkit or creating a pool`() {
        val context = NexusContext.create()
        try {
            val before = context.getBeanNames().toSet()
            context.registerDiscordBindings()
            assertEquals(
                setOf("discordAccountLinkSubscription", "discordGuildProfileSubscription"),
                context.getBeanNames().toSet() - before,
            )
        } finally {
            context.close()
        }
    }

    @Test
    fun `unavailable DiscordSRV preserves both disabled subscription contracts`() {
        val plugins = mockk<PluginManager>()
        every { plugins.getPlugin("DiscordSRV") } returns null
        mockkStatic(Bukkit::class)
        try {
            every { Bukkit.getPluginManager() } returns plugins
            val context = NexusContext.create()
            try {
                context.registerDiscordBindings()
                val links = context.getBean<DiscordAccountLinkSubscription>()
                val profiles = context.getBean<DiscordGuildProfileSubscription>()
                assertIs<UnavailableDiscordAccountLinkSubscription>(links)
                assertIs<UnavailableDiscordGuildProfileSubscription>(profiles)
                assertSame(links, context.getBean<DiscordAccountLinkSubscription>())
                assertSame(profiles, context.getBean<DiscordGuildProfileSubscription>())
            } finally {
                context.close()
            }
        } finally {
            unmockkStatic(Bukkit::class)
        }
    }
}
