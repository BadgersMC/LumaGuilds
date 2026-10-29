package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class GuildDialogTextTest {
    private val plain = PlainTextComponentSerializer.plainText()

    @Test
    fun longWordsAndSmallCapsWrapByPixelsAndKeepFormattingAndClickEvents() {
        val value = "ᴀʙᴄ This guild has a very long description " + "W".repeat(50)
        val click = ClickEvent.runCommand("/guild recruitment")
        val component = Component.text(value, NamedTextColor.GOLD).decorate(TextDecoration.BOLD).clickEvent(click)
        val rows = assertNotNull(GuildDialogText.rows(component, 498))
        assertTrue(rows.size > 2)
        assertTrue(rows.all { it.second <= 498 })
        assertEquals(value, rows.joinToString("") { plain.serialize(it.first) })
        assertTrue(
            rows.flatMap { it.first.children() }.all { it.clickEvent() == click && it.color() == NamedTextColor.GOLD },
        )
    }

    @Test
    fun unknownFontsAndNonTextComponentsUseReadableFallbackInsteadOfGuessing() {
        assertNull(GuildDialogText.rows(Component.text("Custom").font(Key.key("other:font")), 498))
        assertNull(GuildDialogText.rows(Component.translatable("example.key"), 498))
        assertNull(
            GuildDialogCards.resolve({ id ->
                if (id == "guild_dialog_layout_v2") {
                    null
                } else {
                    ResolvedNexoGlyph(
                        "\uE500",
                        "lumaguilds:dialogs",
                        false,
                        id,
                    )
                }
            }, "enthusia"),
        )
    }

    @Test
    fun labelsFitNativeButtonAndShortLabelsAreUnchanged() {
        val original = Component.text("Recruitment Method: Apply Through Discord")
        val fitted = GuildDialogText.buttonLabel(original, 328)
        assertTrue(plain.serialize(fitted).endsWith("..."))
        assertTrue(assertNotNull(GuildDialogText.rows(fitted, 328)).single().second <= 328)
        val short = Component.text("Joining fee")
        assertEquals(short, GuildDialogText.buttonLabel(short, 328))
    }
}
