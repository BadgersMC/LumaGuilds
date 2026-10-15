package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

private const val CARD_FONT = "lumaguilds:dialogs"
private val CARD_SPACING = Key.key("lumaguilds:dialog_spacing")

/** Each background occupies one body line; no artwork extends over native controls. */
internal class GuildDialogCards(private val parts: Map<String, Component>) {
    fun render(lines: List<Component>): Component {
        val result = Component.text().append(parts.getValue("card_top")).append(Component.newline())
        for (line in lines) {
            result.append(parts.getValue("card_line"))
                .append(Component.text("\uE000", NamedTextColor.WHITE).font(CARD_SPACING))
                .append(Component.text("\uE001", NamedTextColor.WHITE).font(CARD_SPACING))
                .append(line).append(Component.newline())
        }
        return result.append(parts.getValue("card_bottom")).build()
    }

    companion object {
        fun resolve(resolver: NexoGlyphResolver, style: String): GuildDialogCards? {
            val parts = mutableMapOf<String, Component>()
            for (part in listOf("card_top", "card_line", "card_bottom")) {
                val id = "guild_dialog_${style}_$part"
                val glyph = resolveOptionalGlyph(resolver, id) ?: return null
                if (!glyph.isDialogArt() || !glyph.matchesId(id)) {
                    return null
                }
                parts[part] = Component.text(glyph.character, NamedTextColor.WHITE).font(Key.key(CARD_FONT))
            }
            return GuildDialogCards(parts)
        }
    }
}
