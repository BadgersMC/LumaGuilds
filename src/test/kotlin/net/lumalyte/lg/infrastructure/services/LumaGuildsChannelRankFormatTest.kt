package net.lumalyte.lg.infrastructure.services

import dev.rosewood.rosechat.hook.channel.ChannelProvider
import io.mockk.mockk
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals

class LumaGuildsChannelRankFormatTest {

    @Test
    fun `guild channel decorates chat and shout formats with current guild rank`() {
        val channel = LumaGuildsChannel(mockk<ChannelProvider>(relaxed = true))
        val config = YamlConfiguration().apply {
            set("channel-type", "GUILD")
            set("formats.chat", "{prefix}{player}{separator}{message}")
            set("formats.shout", "&8[SHOUT] {prefix}{player}{separator}{message}")
        }

        channel.onLoad("guild", config)

        assertEquals(
            "{prefix}&8[&b%lumaguilds_guild_rank%&8]&f {player}{separator}{message}",
            channel.settings.formats["chat"],
        )
        assertEquals(
            "&8[SHOUT] {prefix}&8[&b%lumaguilds_guild_rank%&8]&f {player}{separator}{message}",
            channel.settings.formats["shout"],
        )
    }

    @Test
    fun `ally channel format is not modified by rank restore`() {
        val channel = LumaGuildsChannel(mockk<ChannelProvider>(relaxed = true))
        val config = YamlConfiguration().apply {
            set("channel-type", "ALLY")
            set("formats.chat", "{prefix}{player}{separator}{message}")
        }

        channel.onLoad("guild-ally", config)

        assertEquals(
            "{prefix}{player}{separator}{message}",
            channel.settings.formats["chat"],
        )
    }
    @Test
    fun `guild channel honors custom rank format template`() {
        val channel = LumaGuildsChannel(mockk<ChannelProvider>(relaxed = true))
        val config = YamlConfiguration().apply {
            set("channel-type", "GUILD")
            set("guild-rank-format", "&6<&f<rank>&6>&r ")
            set("formats.chat", "{prefix}{player}{separator}{message}")
        }

        channel.onLoad("guild", config)

        assertEquals(
            "{prefix}&6<&f%lumaguilds_guild_rank%&6>&r {player}{separator}{message}",
            channel.settings.formats["chat"],
        )
    }
}
