package net.lumalyte.lg.infrastructure.i18n

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GuiTextStylerTest {
    private val plain = PlainTextComponentSerializer.plainText()

    @Test
    fun `menu styling small caps translated text but preserves numbers punctuation glyphs and proper names`() {
        val translated = Component.text("Guild Level 42: ")
            .append(Component.text("MyGuild", NamedTextColor.GOLD))
            .append(Component.text(" "))

        val styled = GuiTextStyler.style(translated, protectedText = setOf("MyGuild"))

        assertEquals("ɢᴜɪʟᴅ ʟᴇᴠᴇʟ 42: MyGuild ", plain.serialize(styled))
        assertEquals(0xFF000000.toInt(), styled.shadowColor()?.value())
        assertEquals(NamedTextColor.GOLD, styled.children()[0].color())
    }

    @Test
    fun `chat styling adds shadow without changing case`() {
        val styled = GuiTextStyler.shadow(Component.text("Guild Chat for MyGuild"))

        assertEquals("Guild Chat for MyGuild", plain.serialize(styled))
        assertEquals(0xFF000000.toInt(), styled.shadowColor()?.value())
    }

    @Test
    fun `small caps conversion does not add styling by itself`() {
        val converted = GuiTextStyler.smallCaps(Component.text("Rank 10"))

        assertEquals("ʀᴀɴᴋ 10", plain.serialize(converted))
        assertNull(converted.shadowColor())
    }

    @Test
    fun `menu styling disables italics at every component depth while preserving other decoration`() {
        val translated = Component.text("Parent")
            .decorate(TextDecoration.ITALIC)
            .append(Component.text("Child", NamedTextColor.GOLD).decorate(TextDecoration.ITALIC, TextDecoration.BOLD))

        val styled = GuiTextStyler.style(translated)

        assertEquals(TextDecoration.State.FALSE, styled.decoration(TextDecoration.ITALIC))
        assertEquals(TextDecoration.State.FALSE, styled.children().single().decoration(TextDecoration.ITALIC))
        assertEquals(TextDecoration.State.TRUE, styled.children().single().decoration(TextDecoration.BOLD))
        assertEquals(NamedTextColor.GOLD, styled.children().single().color())
    }
}
