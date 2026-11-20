package net.lumalyte.lg.utils

/**
 * Builds ChestGui titles that use Nexo font-glyph overlays for
 * themed guild-menu backgrounds.
 *
 * The themed PNGs have their artwork at canvas origin (0,0) within
 * a 256x256 transparent canvas. The content measures 176 pixels
 * wide x rowHeight tall.
 *
 * Title component structure:
 *
 *   <shift:-8>                    calibrated horizontal offset
 *   <glyph:guild_bg_<theme>_<R>>  background overlay (advances cursor ~256px)
 *   <shift:-162>                  rewind ~80px less — title lands at ~x=86
 *   <title>                       visible title text in the top bar
 *
 * The rewind value of -162 advances the cursor ~80px into the title bar:
 *   D - 8 + 256 - 162 = D + 86
 *   where D = default cursor start, 256 = glyph texture width
 *
 * DO NOT use the neutral theme as a positioning reference — its
 * assets are oversized and are being corrected separately.
 *
 * Horizontal shift -8 is paired with glyph ascent 13; the prior -9/ascent 14 drew
 * the art one GUI pixel left and up. The -162 rewind retains the title offset.
 *
 * Glyph naming: guild_bg_<theme>_<rows>_row
 */
object MenuTitleBuilder {

    private const val HORIZONTAL_OFFSET: String = "<shift:-8>"

    private const val REWIND_TO_TITLE: String = "<shift:-162>"

    /**
     * Returns a ChestGui title string that renders a Nexo font-glyph
     * background with an optional visible title in the top bar.
     *
     * Result:
     *   <shift:-8><glyph:guild_bg_<theme>_<R>_row><shift:-162><title>
     *
     * @param theme  GUI background theme (default: Enthusia; retired themes resolve to it)
     * @param rows   Inventory row count (3-6)
     * @param title  Optional visible title text (default: empty = no title)
     * @return       Title string for the ChestGui constructor.
     */
    fun build(theme: GuiTheme = GuiTheme.DEFAULT, rows: Int, title: String = ""): String {
        val theme = theme.resolved()
        // Vanilla style: plain chest title. White text is meant for the dark themed backgrounds,
        // so it is reset to the default chest colour; other colours are kept.
        val title = fit(title)
        if (!theme.hasBackground) return title.replace("§f", "§r")
        val themeKey = theme.name.lowercase()
        val glyphName = "guild_bg_${themeKey}_${rows}_row"
        val prefix = "${HORIZONTAL_OFFSET}<glyph:${glyphName}>"
        return if (title.isNotEmpty()) {
            "${prefix}${REWIND_TO_TITLE}${title}"
        } else {
            prefix
        }
    }

    /**
     * REQ-232: GUI pixels available to a title. It starts about 13px into the 176px menu and must
     * stop before the right edge (measured on TEST: "DIPLOMATIC RELATIONS - GoldProofOct26" overran).
     */
    const val TITLE_WIDTH: Int = 155

    private const val ELLIPSIS = "..."

    /** Shortens [title] with "..." so its visible text fits [TITLE_WIDTH]; § colour codes are kept. */
    fun fit(title: String, maxWidth: Int = TITLE_WIDTH): String {
        if (width(title) <= maxWidth) return title
        val budget = maxWidth - width(ELLIPSIS)
        val out = StringBuilder()
        var used = 0
        var i = 0
        while (i < title.length) {
            val c = title[i]
            if (c == '§' && i + 1 < title.length) {
                out.append(c).append(title[i + 1]); i += 2; continue
            }
            val w = charWidth(c)
            if (used + w > budget) break
            out.append(c); used += w; i++
        }
        return out.toString().trimEnd() + ELLIPSIS
    }

    /** Visible width in GUI pixels with the vanilla font, ignoring § colour codes. */
    fun width(text: String): Int {
        var total = 0
        var i = 0
        while (i < text.length) {
            if (text[i] == '§' && i + 1 < text.length) { i += 2; continue }
            total += charWidth(text[i]); i++
        }
        return total
    }

    /** Advance (glyph plus 1px spacing) of a vanilla default-font character. */
    private fun charWidth(c: Char): Int = when (c) {
        ' ' -> 4
        '!', '\'', ',', '.', ':', ';', '|', 'i' -> 2
        '`', 'l' -> 3
        'I', 't', '[', ']', '(', ')', '{', '}', '"', '*' -> 4
        'f', 'k', '<', '>' -> 5
        '@', '~' -> 7
        else -> 6
    }
}
