package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildCosmeticUnlockService
import net.lumalyte.lg.domain.entities.HolidayGuildEmoji
import org.bukkit.entity.Player
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class HolidayGuildEmojiTest {
    private val guildId = UUID.randomUUID()
    private val cosmetics = mockk<GuildCosmeticUnlockService>()
    private val actor = mockk<Player>(relaxed = true)
    private val resolver =
        NexoGlyphResolver { name ->
            ResolvedNexoGlyph(HolidayGuildEmoji.CHARACTER, HolidayGuildEmoji.FONT, false, name)
        }
    private val service = NexoEmojiService(mockk<ConfigService>(), resolver, cosmetics)

    @DisplayName("only the exact existing pumpkin asset is admitted from non-emoji glyphs")
    @Test
    fun glyphAdmission() {
        assertTrue(service.doesEmojiExist(HolidayGuildEmoji.PLACEHOLDER))
        assertEquals("<font:minecraft:default>\uA0A0</font>", service.emojiToFontTag(HolidayGuildEmoji.PLACEHOLDER))
        assertFalse(service.doesEmojiExist(":halloween_icons_skull:"))
        assertFalse(service.doesEmojiExist(":alias:"))
        val wrong =
            NexoEmojiService(
                mockk<ConfigService>(),
                NexoGlyphResolver { ResolvedNexoGlyph("X", HolidayGuildEmoji.FONT, false, HolidayGuildEmoji.GLYPH) },
            )
        assertFalse(wrong.doesEmojiExist(HolidayGuildEmoji.PLACEHOLDER))
    }

    @DisplayName("guild ownership gates selection despite wildcard personal permission")
    @Test
    fun guildOwnership() {
        every { actor.hasPermission(any<String>()) } returns true
        every { cosmetics.ownsPumpkinEmoji(guildId) } returns false
        assertFalse(service.hasGuildEmojiPermission(actor, HolidayGuildEmoji.PLACEHOLDER, guildId))
        assertFalse(service.hasGuildEmojiPermission(actor, HolidayGuildEmoji.PLACEHOLDER, null))
        every { cosmetics.ownsPumpkinEmoji(guildId) } returns true
        assertTrue(service.hasGuildEmojiPermission(actor, HolidayGuildEmoji.PLACEHOLDER, guildId))
        val otherGuild = UUID.randomUUID()
        every { cosmetics.ownsPumpkinEmoji(otherGuild) } returns false
        assertFalse(service.hasGuildEmojiPermission(actor, HolidayGuildEmoji.PLACEHOLDER, otherGuild))
    }
}
