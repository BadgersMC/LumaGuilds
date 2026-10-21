package net.lumalyte.lg.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.TextReplacementConfig
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

/**
 * Menu titles carry the guild background as a `<glyph:guild_bg_…>` tag (see [MenuTitleBuilder]).
 * Glyph characters only draw in the font Nexo registers them in (`nexo:default`), and the plain
 * title path can hand the character to the client in the default font, where it shows as a box.
 * This swaps each background tag for Nexo's own glyph component, which carries the right font.
 * Shift tags are left as text: their font is chained into `minecraft:default`, so they resolve
 * either way.
 */
object MenuTitleGlyphs {
    private val BACKGROUND_TAG = Regex("<glyph:(guild_bg_[a-z0-9_]+)>")

    /** Whether [title] carries a guild background glyph tag. */
    fun hasBackgroundGlyph(title: Component): Boolean =
        BACKGROUND_TAG.containsMatchIn(PlainTextComponentSerializer.plainText().serialize(title))

    /** [title] with every resolvable background tag replaced by `lookup(id)`; unchanged otherwise. */
    fun withGlyphFonts(title: Component, lookup: (String) -> Component?): Component {
        val ids =
            BACKGROUND_TAG.findAll(PlainTextComponentSerializer.plainText().serialize(title))
                .map { it.groupValues[1] }
                .distinct()
                .toList()
        if (ids.isEmpty()) return plateForResolvedBackground(title, lookup)
        val resolved = ids.mapNotNull { id -> lookup(id)?.let { background ->
            val plate = lookup(id.replace("guild_bg_", "guild_inventory_label_"))
            id to if (plate == null) background else withPlate(background, plate)
        } }.toMap()
        if (resolved.isEmpty()) return title
        return title.replaceText(
            TextReplacementConfig.builder()
                .match(BACKGROUND_TAG.toPattern())
                .replacement { match, builder -> resolved[match.group(1)] ?: builder }
                .build(),
        )
    }

    /** Nexo can resolve tags before our InventoryOpenEvent listener sees the title. */
    private fun plateForResolvedBackground(title: Component, lookup: (String) -> Component?): Component {
        val serializer = PlainTextComponentSerializer.plainText()
        val text = serializer.serialize(title)
        for (theme in GuiTheme.SELECTABLE.filter { it.hasBackground }) {
            for (rows in 1..6) {
                val suffix = "${theme.name.lowercase()}_${rows}_row"
                val background = lookup("guild_bg_$suffix") ?: continue
                val glyph = serializer.serialize(background)
                // Restrict recognition to one non-ASCII registered glyph, never ordinary title text.
                if (glyph.codePointCount(0, glyph.length) != 1 || glyph.codePointAt(0) < 128) continue
                if (!text.contains(glyph)) continue
                val plate = lookup("guild_inventory_label_$suffix") ?: return title
                val plateText = serializer.serialize(plate)
                if (plateText.isEmpty() || text.contains(plateText)) return title
                return title.replaceText(TextReplacementConfig.builder()
                    .matchLiteral(glyph).replacement(withPlate(background, plate)).build())
            }
        }
        return title
    }

    private fun withPlate(background: Component, plate: Component): Component = background
        .append(Component.text("\uE000").font(Key.key("lumaguilds:inventory_label_spacing")))
        .append(plate.color(net.kyori.adventure.text.format.NamedTextColor.WHITE)
            .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false)
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
}
