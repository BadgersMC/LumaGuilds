package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextDecoration
import net.lumalyte.lg.utils.GuiTheme
import kotlin.math.ceil

/**
 * REQ-236: the gold, full-width "feature" button drawn in a dialog body (e.g. Browse Recruiting
 * Guilds). Art is one 360-wide size from the pack's dialog_action_feature_v1 font.
 */
internal object GuildDialogFeatureButton {
    /** Width of the art: two 179px halves. */
    const val ART = 358

    /**
     * Width of the dialog body that holds the button. The client wraps a plain message at its
     * width minus 8, and the art briefly reaches ART + 1 while its halves are drawn, so the body
     * must be at least ART + 9 or the right half wraps onto a second line.
     */
    const val BODY_WIDTH = ART + 10
    const val FONT = "lumaguilds:dialog_action_feature_v1"
    private const val CONTRACT_ID = "guild_dialog_feature_v1"
    private val font = Key.key(FONT)
    private val TEXT = net.kyori.adventure.text.format.TextColor.color(0xFFF6D8)

    private fun glyph(code: Int) = Component.text(code.toChar(), NamedTextColor.WHITE).font(font)
        .shadowColor(ShadowColor.none()).decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false)

    private fun pad(pixels: Int): Component = Component.text(buildString {
        for (bit in 0..10) if (pixels and (1 shl bit) != 0) append((0xE010 + bit).toChar())
    }, NamedTextColor.WHITE).font(font).shadowColor(ShadowColor.none())

    fun available(theme: GuiTheme, resolver: NexoGlyphResolver = NexoPublicGlyphResolver): Boolean =
        theme.resolved().hasBackground &&
            resolveOptionalGlyph(resolver, CONTRACT_ID)?.isDialogContract(CONTRACT_ID, "lumaguilds:dialog_action_contract") == true

    /** Advance in half pixels of a glyph in the feature font, for layout measurement. */
    fun halfAdvance(code: Int): Int? = when (code) {
        0xE120, 0xE121 -> 360
        0xE000 -> -2
        0xE001 -> -358
        0xE002 -> -558
        0xE003 -> -718
        in 0xE010..0xE01A -> 2 shl (code - 0xE010)
        else -> null
    }

    /** The button art with [content] (icon and text) centred on it, or null when it cannot be centred exactly. */
    fun label(content: Component): Component? {
        val coloured = content.colorIfAbsent(TEXT)
        val row = GuildDialogText.rows(coloured, (ART - 16) * 2) { font, _ ->
            if (font == "lumaguilds:dialog_icons") 22 else null
        }?.singleOrNull() ?: return null
        val contentWidth = ceil(row.second / 2.0).toInt()
        if (contentWidth > ART - 16) return null
        val left = (ART - contentWidth) / 2
        return Component.text().append(glyph(0xE120)).append(glyph(0xE000)).append(glyph(0xE121)).append(glyph(0xE003))
            .append(pad(left)).append(row.first).append(pad(ART - left - contentWidth)).build()
    }

    /** Without the art (vanilla theme or an older pack): bold gold text, still one clickable line. */
    fun fallback(content: Component): Component =
        Component.text("» ", NamedTextColor.GOLD).append(content.colorIfAbsent(NamedTextColor.GOLD))
            .append(Component.text(" «", NamedTextColor.GOLD)).decoration(TextDecoration.BOLD, true)
}
