package net.lumalyte.lg.utils

/**
 * Builds ChestGui titles that use Nexo font-glyph overlays for
 * themed guild-menu backgrounds.
 *
 * The themed PNGs have their artwork at canvas origin (0,0) within
 * a 256x256 transparent canvas. The content measures 176 pixels
 * wide x rowHeight tall (e.g. 168 px for 3 rows).
 *
 * To align this artwork with the native Minecraft inventory window
 * (also 176 px wide):
 *
 *   shift:20  moves the cursor right by 20 pixels so the 176-wide
 *             artwork starts at column 0 of the window.
 *
 *   ascent: 12  must be set on each glyph in the Nexo resource pack
 *               (the font baseline is ~12 px from the window top).
 *
 * DO NOT use the neutral theme as a positioning reference — its
 * assets are oversized and are being corrected separately.
 *
 * Glyph naming: guild_bg_<theme>_<rows>_row
 * e.g. guild_bg_neutral_3_row, guild_bg_emberstone_4_row
 */
object MenuTitleBuilder {

    /** Horizontal prefix that places the glyph at the window origin. */
    private const val GLYPH_PREFIX: String = "<shift:6>"

    /**
     * Returns a ChestGui title string that renders a Nexo font-glyph
     * background overlay for the given theme and row count.
     *
     * Result: <shift:-8><glyph:guild_bg_<theme>_<rows>_row>
     *
     * @param theme  GUI background theme (default: NEUTRAL)
     * @param rows   Inventory row count (3-6)
     * @return       Title string for the ChestGui constructor.
     */
    fun build(theme: GuiTheme = GuiTheme.NEUTRAL, rows: Int): String {
        val themeKey = theme.name.lowercase()
        val glyphName = "guild_bg_${themeKey}_${rows}_row"
        return "${GLYPH_PREFIX}<glyph:${glyphName}>"
    }
}