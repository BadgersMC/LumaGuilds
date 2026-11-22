package net.lumalyte.lg.infrastructure.services

import dev.rosewood.rosechat.chat.channel.Channel
import dev.rosewood.rosechat.hook.channel.ChannelProvider
import dev.rosewood.rosechat.manager.ChannelManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class GuildChannelProviderShutdownTest {
    private val provider = LumaGuildsChannelProvider(mockk())
    private val other = mockk<ChannelProvider>()
    private val guild = mockk<Channel> { every { provider } returns this@GuildChannelProviderShutdownTest.provider }
    private val global = mockk<Channel> {
        every { provider } returns other
        every { settings.isDefault } returns true
    }
    private val channels = mutableMapOf("guild" to guild, "global" to global)
    private val providers = mutableMapOf("lumaguilds" to provider as ChannelProvider, "rosechat" to other)
    private val manager = mockk<ChannelManager>(relaxed = true) {
        every { channelProviders } returns providers
        every { channels } returns this@GuildChannelProviderShutdownTest.channels
        every { defaultChannel } returns guild
    }

    @Test fun shutdownReleasesOnlyOwnedChannelsAndRestoresTheOtherDefault() {
        provider.unregisterFrom(manager)
        assertEquals(mapOf("rosechat" to other), providers)
        assertEquals(mapOf("global" to global), channels)
        verify { manager.defaultChannel = global }
    }

    @Test fun replacementProviderSurvivesAnOlderRuntimeShutdown() {
        providers["lumaguilds"] = other
        provider.unregisterFrom(manager)
        assertEquals(other, providers["lumaguilds"])
        assertEquals(mapOf("global" to global), channels)
    }
}
