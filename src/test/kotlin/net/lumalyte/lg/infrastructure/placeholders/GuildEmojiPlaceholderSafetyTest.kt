package net.lumalyte.lg.infrastructure.placeholders

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.services.NexoEmojiService
import net.lumalyte.lg.infrastructure.services.NexoGlyphResolver
import net.lumalyte.lg.infrastructure.services.ResolvedNexoGlyph
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class GuildEmojiPlaceholderSafetyTest {
    @AfterEach
    fun cleanup() {
        stopKoin()
    }

    @Test
    fun `saved menu emoji is absent from TAB and MiniMessage placeholders`() {
        stopKoin()
        val guildId = UUID.randomUUID()
        val playerId = UUID.randomUUID()
        val player = mockk<Player>()
        every { player.uniqueId } returns playerId
        val memberService = mockk<MemberService>()
        every { memberService.getPlayerGuilds(playerId) } returns setOf(guildId)
        val guildService = mockk<GuildService>()
        every { guildService.getGuild(guildId) } returns Guild(
            guildId, "Vegas", emoji = ":guild_bg_enthusia_6_row:", createdAt = Instant.EPOCH,
        )
        val emojiService = NexoEmojiService(mockk(), NexoGlyphResolver {
            ResolvedNexoGlyph("ꐘ", "nexo:default", false)
        })
        startKoin {
            modules(module {
                single<GuildService> { guildService }
                single<MemberService> { memberService }
                single<NexoEmojiService> { emojiService }
            })
        }
        val expansion = LumaGuildsExpansion()
        listOf("guild_emoji", "guild_emoji_minimessage", "guild_emoji_font").forEach {
            assertEquals("", expansion.onPlaceholderRequest(player, it))
        }
        assertEquals("Vegas", expansion.onPlaceholderRequest(player, "guild_name"))
    }
}
