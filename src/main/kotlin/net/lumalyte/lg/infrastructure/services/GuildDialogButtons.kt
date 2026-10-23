package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextDecoration
import net.lumalyte.lg.utils.GuiTheme
import kotlin.math.ceil

/** Label-only artwork: native buttons still own their full hitbox, focus and callbacks. */
internal class GuildDialogButtons private constructor(theme: GuiTheme) {
    private val font = Key.key("lumaguilds:dialog_actions_${theme.name.lowercase()}_v1")

    private fun glyph(code: Int) = Component.text(code.toChar(), NamedTextColor.WHITE).font(font)
        .shadowColor(ShadowColor.none())
        .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false)

    private fun pad(pixels: Int): Component = Component.text(buildString {
        for (bit in 0..10) if (pixels and (1 shl bit) != 0) append((0xE010 + bit).toChar())
    }, NamedTextColor.WHITE).font(font).shadowColor(ShadowColor.none())
        .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false)

    /** Unknown/client-translated text keeps native rendering instead of estimating its width. */
    fun label(text: Component, width: Int): Component? {
        val index = WIDTHS.indexOf(width).takeIf { it >= 0 } ?: return null
        val clipped = GuildDialogText.buttonLabel(text, (width - 16) * 2)
        val row = GuildDialogText.rows(clipped, (width - 16) * 2) { key, _ ->
            if (key == "lumaguilds:dialog_icons") ICON_HALF_WIDTH else null
        }?.singleOrNull() ?: return null
        val contentWidth = ceil(row.second / 2.0).toInt()
        val inside = width - 2
        if (contentWidth > inside) return null
        val left = (inside - contentWidth) / 2
        val code = 0xE100 + index * GLYPH_STRIDE
        return Component.text().append(glyph(code)).append(glyph(0xE000))
            .append(glyph(code + 1)).append(glyph(0xE001 + index))
            .append(pad(left)).append(row.first).append(pad(inside - left - contentWidth)).build()
    }

    companion object {
        val WIDTHS = listOf(180, 280, 360)
        const val PANEL_WIDTH = 360
        private const val ICON_HALF_WIDTH = 22
        private const val GLYPH_STRIDE = 0x10

        fun resolve(theme: GuiTheme, resolver: NexoGlyphResolver = NexoPublicGlyphResolver): GuildDialogButtons? {
            val selected = theme.resolved()
            if (!selected.hasBackground) return null
            val id = "guild_dialog_actions_${selected.name.lowercase()}_v1"
            return resolveOptionalGlyph(resolver, id)
                ?.takeIf { it.isDialogContract(id, "lumaguilds:dialog_action_contract") }
                ?.let { GuildDialogButtons(selected) }
        }
    }
}
