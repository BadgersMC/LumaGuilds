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

/** Regression coverage for guild emoji validation and display. */
internal class GuildEmojiPlaceholderSafetyTest {
    /** Releases the test dependency container. */
    @AfterEach
    fun cleanup() {
        stopKoin()
    }

    /** Saved menu glyphs are hidden by each real PAPI emoji field while the guild name survives. */
    @Test
    fun omitsSavedMenuGlyphs() {
        stopKoin()
        val guildId = UUID.randomUUID()
        val playerId = UUID.randomUUID()
        val player = mockk<Player>()
        every { player.uniqueId } returns playerId
        val memberService = mockk<MemberService>()
        every { memberService.getPlayerGuilds(playerId) } returns setOf(guildId)
        val guildService = mockk<GuildService>()
        val guild = Guild(guildId, "Vegas", emoji = ":guild_bg_enthusia_6_row:", createdAt = Instant.EPOCH)
        every { guildService.getGuild(guildId) } returns guild
        val menuGlyph = ResolvedNexoGlyph("ꐘ", "nexo:default", false)
        val emojiService = NexoEmojiService(mockk(), NexoGlyphResolver { menuGlyph })
        startKoin {
            modules(
                module {
                    single<GuildService> { guildService }
                    single<MemberService> { memberService }
                    single<NexoEmojiService> { emojiService }
                },
            )
        }
        val expansion = LumaGuildsExpansion()
        listOf("guild_emoji", "guild_emoji_minimessage", "guild_emoji_font").forEach {
            assertEquals("", expansion.onPlaceholderRequest(player, it))
        }
        assertEquals("Vegas", expansion.onPlaceholderRequest(player, "guild_name"))
    }
}
