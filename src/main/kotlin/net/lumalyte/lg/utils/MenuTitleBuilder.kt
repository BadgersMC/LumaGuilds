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
 *   <shift:-9>                    calibrated horizontal offset
 *   <glyph:guild_bg_<theme>_<R>>  background overlay (advances cursor ~256px)
 *   <shift:-161>                  rewind ~80px less — title lands at ~x=86
 *   §f<title>                     visible title text in the top bar
 *
 * The rewind value of -161 advances the cursor ~80px into the title bar:
 *   D - 9 + 256 - 161 = D + 86
 *   where D = default cursor start, 256 = glyph texture width
 *
 * DO NOT use the neutral theme as a positioning reference — its
 * assets are oversized and are being corrected separately.
 *
 * Glyph naming: guild_bg_<theme>_<rows>_row
 */
object MenuTitleBuilder {

    /** Calibrated horizontal offset placing the glyph at the window origin. */
    private const val HORIZONTAL_OFFSET: String = "<shift:-9>"

    /** Rewind past the 256-pixel glyph advance, landing title ~86px from default start. */
    private const val REWIND_TO_TITLE: String = "<shift:-161>"

    /**
     * Returns a ChestGui title string that renders a Nexo font-glyph
     * background with an optional visible title in the top bar.
     *
     * Result:
     *   <shift:-9><glyph:guild_bg_<theme>_<R>_row><shift:-161>§f<title>
     *
     * @param theme  GUI background theme (default: NEUTRAL)
     * @param rows   Inventory row count (3-6)
     * @param title  Optional visible title text (default: empty = no title)
     * @return       Title string for the ChestGui constructor.
     */
    fun build(theme: GuiTheme = GuiTheme.NEUTRAL, rows: Int, title: String = ""): String {
        val themeKey = theme.name.lowercase()
        val glyphName = "guild_bg_${themeKey}_${rows}_row"
        val prefix = "${HORIZONTAL_OFFSET}<glyph:${glyphName}>"
        return if (title.isNotEmpty()) {
            "${prefix}${REWIND_TO_TITLE}§f${title}"
        } else {
            prefix
        }
    }
}