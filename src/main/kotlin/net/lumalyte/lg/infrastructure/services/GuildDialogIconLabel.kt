package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration

private const val ICON_FONT = "lumaguilds:dialog_icons"

/** Item texture glyphs are optional: a missing private pack retains the native text action. */
internal fun guildDialogIconLabel(
    text: Component,
    itemId: String,
    resolver: NexoGlyphResolver = NexoPublicGlyphResolver,
): Component {
    val id = "guild_dialog_icon_$itemId"
    val glyph =
        resolveOptionalGlyph(resolver, id)?.takeIf {
            !it.isEmoji && it.font == ICON_FONT && it.character.codePointCount(0, it.character.length) == 1 &&
                it.matchesId(id)
        } ?: return text
    val icon =
        Component.text(glyph.character, NamedTextColor.WHITE).font(Key.key(ICON_FONT))
            .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false)
    return Component.empty().append(icon).append(Component.space()).append(text)
}
