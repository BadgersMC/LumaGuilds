package net.lumalyte.lg.utils

/**
 * Builds ChestGui titles that use Nexo font-glyph overlays for
 * themed guild-menu backgrounds.
 *
 * The glyph naming convention is:
 *   guild_bg_[theme]_[rows]_row
 *
 * e.g. guild_bg_neutral_3_row, guild_bg_emberstone_4_row
 *
 * Each glyph must be defined in the Nexo glyphs configuration and
 * reference the corresponding texture at:
 *   assets/minecraft/textures/gui/[theme/]guild_menu_[theme]_[rows]_row.png
 */
object MenuTitleBuilder {

    /**
     * Returns a ChestGui title string that renders a Nexo font-glyph
     * background overlay for the given theme and row count.
     *
     * The shift value centres the 256‑tall glyph over a standard
     * 3–6‑row chest GUI. Callers should use this in place of a
     * hard‑coded title string.
     */
    fun build(theme: GuiTheme = GuiTheme.NEUTRAL, rows: Int): String {
        val themeKey = theme.name.lowercase()
        val glyphName = "guild_bg_${themeKey}_${rows}_row"
        return "<shift:-37><glyph:$glyphName>"
    }
}