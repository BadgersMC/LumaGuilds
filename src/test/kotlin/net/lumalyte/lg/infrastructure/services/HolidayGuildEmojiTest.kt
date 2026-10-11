package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildCosmeticUnlockService
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

internal class HolidayGuildEmojiTest {
    private val guildId = UUID.randomUUID()
    private val cosmetics = mockk<GuildCosmeticUnlockService>()
    private val actor = mockk<Player>(relaxed = true)
    private val resolver = NexoGlyphResolver { name ->
        ResolvedNexoGlyph("\uA0A0", "minecraft:default", false, name)
    }
    private val service = NexoEmojiService(mockk<ConfigService>(), resolver, cosmetics)

    @Test
    fun `only the existing pumpkin asset is admitted from non emoji glyphs`() {
        assertTrue(service.doesEmojiExist(":pumpkin_king_icon:"))
        assertEquals("<font:minecraft:default>\uA0A0</font>", service.emojiToFontTag(":pumpkin_king_icon:"))
        assertFalse(service.doesEmojiExist(":halloween_icons_skull:"))
        assertFalse(service.doesEmojiExist(":alias:"))
        val wrong = NexoEmojiService(mockk<ConfigService>(), NexoGlyphResolver {
            ResolvedNexoGlyph("X", "minecraft:default", false, "pumpkin_king_icon")
        })
        assertFalse(wrong.doesEmojiExist(":pumpkin_king_icon:"))
    }

    @Test
    fun `guild ownership gates selection independently of wildcard actor permission`() {
        every { actor.hasPermission(any<String>()) } returns true
        every { cosmetics.ownsPumpkinEmoji(guildId) } returns false
        assertFalse(service.hasGuildEmojiPermission(actor, ":pumpkin_king_icon:", guildId))
        assertFalse(service.hasGuildEmojiPermission(actor, ":pumpkin_king_icon:", null))
        every { cosmetics.ownsPumpkinEmoji(guildId) } returns true
        assertTrue(service.hasGuildEmojiPermission(actor, ":pumpkin_king_icon:", guildId))
        val otherGuild = UUID.randomUUID()
        every { cosmetics.ownsPumpkinEmoji(otherGuild) } returns false
        assertFalse(service.hasGuildEmojiPermission(actor, ":pumpkin_king_icon:", otherGuild))
    }
}
