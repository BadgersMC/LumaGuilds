package net.lumalyte.lg.infrastructure.services

import dev.rosewood.rosechat.chat.channel.ChannelMessageOptions
import dev.rosewood.rosechat.hook.channel.ChannelProvider
import dev.rosewood.rosechat.message.RosePlayer
import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.GuildChatRankSettingsService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Rank
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LumaGuildsChannelRankVisibilityTest {
    @AfterEach fun cleanup() = stopKoin()

    @Test
    fun `guild setting changes per message without changing shared channel format`() {
        val guilds = mockk<GuildService>()
        val members = mockk<MemberService>()
        val ranks = mockk<RankService>()
        val settings = mockk<GuildChatRankSettingsService>()
        stopKoin()
        startKoin { modules(module {
            single { guilds }; single { members }; single { ranks }; single { settings }
        }) }
        val channel = LumaGuildsChannel(mockk<ChannelProvider>(relaxed = true))
        channel.onLoad("guild", YamlConfiguration().apply {
            set("channel-type", "GUILD")
            set("formats.chat", "{player}: {message}")
        })
        val sharedFormat = channel.settings.formats["chat"]
        val firstGuild = Guild(UUID.randomUUID(), "First", createdAt = Instant.now())
        val secondGuild = Guild(UUID.randomUUID(), "Second", createdAt = Instant.now())
        val first = mockk<RosePlayer>()
        val second = mockk<RosePlayer>()
        val firstId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        every { first.uuid } returns firstId
        every { second.uuid } returns secondId
        listOf(firstId to firstGuild, secondId to secondGuild).forEach { (player, guild) ->
            val rank = Rank(UUID.randomUUID(), guild.id, "&aFounder", 0, emptySet())
            every { guilds.getPlayerGuilds(player) } returns setOf(guild)
            every { members.getPlayerRankId(player, guild.id) } returns rank.id
            every { ranks.getRank(rank.id) } returns rank
        }
        every { settings.ranksVisible(firstGuild.id) } returns false
        every { settings.ranksVisible(secondGuild.id) } returns true
        fun options(sender: RosePlayer) = ChannelMessageOptions.Builder()
            .sender(sender)
            .message("Hello")
            .sendToDiscord(false)
            .build()
        assertEquals("{player}: {message}", channel.prepareOptions(options(first)).format())
        val visible = channel.prepareOptions(options(second))
        assertTrue(visible.format().contains("§aFounder§r"))
        assertEquals("Hello", visible.message())
        assertFalse(visible.format().contains(GuildRankChatFormatter.RANK_PLACEHOLDER))
        every { settings.ranksVisible(firstGuild.id) } returns true
        assertTrue(channel.prepareOptions(options(first)).format().contains("§aFounder§r"))
        assertEquals(sharedFormat, channel.settings.formats["chat"])
    }
}
