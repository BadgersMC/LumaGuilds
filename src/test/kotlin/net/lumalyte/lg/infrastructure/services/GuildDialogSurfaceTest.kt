package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.papermc.paper.registry.data.dialog.body.PlainMessageDialogBody
import net.kyori.adventure.text.Component
import net.lumalyte.lg.utils.GuiTheme
import org.junit.jupiter.api.Test
import kotlin.test.*
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import io.papermc.paper.registry.data.dialog.body.ItemDialogBody
import java.nio.file.Files
import java.nio.file.Path

internal class GuildDialogSurfaceTest {
    private fun message(text: Component, width: Int = 280) = mockk<PlainMessageDialogBody> {
        every { contents() } returns text
        every { width() } returns width
    }

    @Test fun nativeWrappedHeightAndInsetsAreReserved() {
        assertEquals(17, GuildDialogSurface.bodyHeight(message(Component.text("Guild"))))
        assertEquals(26, GuildDialogSurface.bodyHeight(message(Component.text("Guild\nDetails"))))
        assertTrue(assertNotNull(GuildDialogSurface.bodyHeight(message(Component.text("Long name ".repeat(20)), 100))) > 100)
    }

    @Test fun stripsEndInsideTheNativeGapBeforeControls() {
        for (height in 1..1100) {
            val painted = assertNotNull(GuildDialogSurface.stripCount(height)) * 9
            assertTrue(painted >= height && painted - height < 9)
        }
        assertNull(GuildDialogSurface.stripCount(2000))
    }

    @Test fun unknownFontsAndVanillaDoNotReceiveEstimatedBackgrounds() {
        assertNull(GuildDialogSurface.bodyHeight(message(Component.text("x").font(net.kyori.adventure.key.Key.key("unknown:font")))))
        assertNull(GuildDialogSurface.resolve(GuiTheme.VANILLA) { error("Vanilla must not query the pack") })
        assertNull(GuildDialogSurface.resolve(GuiTheme.ENTHUSIA) { null })
    }

    @Test fun itemInsetsAndUnknownDescriptionsAreNotGuessed() {
        val item = mockk<ItemDialogBody> {
            every { height() } returns 16
            every { description() } returns message(Component.text("One\nTwo"))
        }
        assertEquals(26, GuildDialogSurface.bodyHeight(item))
        every { item.description() } returns message(Component.text("x").font(Key.key("unknown:font")))
        assertNull(GuildDialogSurface.bodyHeight(item))
    }

    @Test fun allThemesKeepTheOriginalClickableTextAndProduceNativeFixtureWhenRequested() {
        val output = System.getenv("LG_DIALOG_SURFACE_FIXTURE_DIR")?.let(Path::of)
        val serializer = GsonComponentSerializer.gson()
        fun json(c: Component): Any = JsonParser.parseString(serializer.serialize(c))
        fun text(s: String) = Component.text(s)
        for (theme in GuiTheme.SELECTABLE.filter { it.hasBackground }) {
            val surface = assertNotNull(GuildDialogSurface.resolve(theme) { id ->
                ResolvedNexoGlyph("\ue001", "lumaguilds:dialog_surface_contract", false, id)
            })
            for (screen in listOf("manage", "board", "rankings", "wrapped", "scroll")) {
                val bodies = mutableListOf<io.papermc.paper.registry.data.dialog.body.DialogBody>()
                val native = mutableListOf<Map<String, Any>>()
                fun plain(c: Component) {
                    bodies += message(c)
                    native += mapOf("type" to "minecraft:plain_message", "width" to 280, "contents" to json(c))
                }
                plain(text(if (screen == "board") "[All]   Building   PvP   Quests   Social" else "More listing options"))
                val cards = GuildDialogCards(mapOf("card_top" to text("\ue500").font(Key.key("lumaguilds:dialogs")),
                    "card_line" to text("\ue501").font(Key.key("lumaguilds:dialogs")),
                    "card_bottom" to text("\ue502").font(Key.key("lumaguilds:dialogs"))))
                val names = when(screen) {
                    "board" -> listOf("Enthusiast", "Everlights", "New Crown")
                    "rankings" -> listOf("AFK", "Amethyst", "Architects", "Armaeth")
                    else -> listOf("Enthusiast")
                }
                for (name in names) {
                    val left = if (screen in listOf("wrapped", "scroll")) listOf(text(name), text("Long translated description ".repeat(if (screen == "scroll") 55 else 10)))
                        else listOf(text(name), text(if (screen == "rankings") "Progress: 300 / 500" else "Guild level: 26"))
                    val panel = assertNotNull(cards.renderColumns(left, listOf(text("Apply in game"), text("No joining fee"))))
                        .clickEvent(ClickEvent.suggestCommand("sample-only"))
                    val description = message(panel, 268)
                    val item = mockk<ItemDialogBody> {
                        every { height() } returns 16
                        every { description() } returns description
                    }
                    bodies += item
                    native += mapOf("type" to "minecraft:item", "item" to mapOf("id" to "minecraft:red_banner"),
                        "width" to 16, "height" to 16, "show_decorations" to false, "show_tooltip" to false,
                        "description" to mapOf("width" to 268, "contents" to json(panel.clickEvent(null))))
                    assertEquals(ClickEvent.suggestCommand("sample-only"), description.contents().clickEvent())
                }
                val title = text(if (screen == "board") "Guild Recruitment" else if (screen == "rankings") "Quest Rankings" else "Manage Recruitment")
                val heading = assertNotNull(surface.heading(title, text("Local sample - no guild data changes"), bodies))
                // The underlay never edits or reserializes the existing body widget.
                assertTrue(bodies.all { GuildDialogSurface.bodyHeight(it) != null })
                output?.let {
                    Files.createDirectories(it)
                    val value = mapOf("heading" to json(heading), "body" to native,
                        "width" to GuildDialogSurface.WIDTH, "height" to bodies.sumOf { b -> assertNotNull(GuildDialogSurface.bodyHeight(b)) })
                    Files.writeString(it.resolve("${theme.name.lowercase()}-$screen.json"), GsonBuilder().setPrettyPrinting().create().toJson(value))
                }
            }
        }
    }
}
