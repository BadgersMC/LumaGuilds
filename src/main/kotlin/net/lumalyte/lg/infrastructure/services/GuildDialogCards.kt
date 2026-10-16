package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

private const val CARD_FONT = "lumaguilds:dialogs"
private val CARD_SPACING = Key.key("lumaguilds:dialog_spacing_v2")

/** Each background occupies one body line; no artwork extends over native controls. */
internal class GuildDialogCards(private val parts: Map<String, Component>) {
    fun render(lines: List<Component>): Component? {
        // Both cap glyphs advance 257px. Every text row must advance the same amount,
        // otherwise the native centred multiline widget shifts each background separately.
        val rows = lines.flatMap { GuildDialogText.rows(it, 498) ?: return null }
        val result = Component.text().append(parts.getValue("card_top")).append(Component.newline())
        for ((line, width) in rows) {
            result.append(parts.getValue("card_line"))
                .append(Component.text("\uE000", NamedTextColor.WHITE).font(CARD_SPACING))
                .append(Component.text("\uE001", NamedTextColor.WHITE).font(CARD_SPACING))
                .append(line)
                .append(padding(506 - width))
                .append(Component.newline())
        }
        return result.append(parts.getValue("card_bottom")).build()
    }

    private fun padding(halfPixels: Int): Component {
        val characters =
            buildString {
                for (bit in 0..9) if (halfPixels and (1 shl bit) != 0) append((0xE010 + bit).toChar())
            }
        return Component.text(characters, NamedTextColor.WHITE).font(CARD_SPACING)
            .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false)
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
    }

    companion object {
        fun resolve(
            resolver: NexoGlyphResolver,
            style: String,
        ): GuildDialogCards? {
            val marker = resolveOptionalGlyph(resolver, "guild_dialog_layout_v2")
            if (marker == null || !marker.isDialogArt() || !marker.matchesId("guild_dialog_layout_v2")) return null
            val parts = mutableMapOf<String, Component>()
            for (part in listOf("card_top", "card_line", "card_bottom")) {
                val id = "guild_dialog_${style}_$part"
                val glyph =
                    resolveOptionalGlyph(resolver, id)?.takeIf {
                        it.isDialogArt() && it.matchesId(id)
                    } ?: return null
                parts[part] = Component.text(glyph.character, NamedTextColor.WHITE).font(Key.key(CARD_FONT))
            }
            return GuildDialogCards(parts)
        }
    }
}
