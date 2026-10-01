package net.lumalyte.lg.utils

/**
 * GUI background themes for guild menus.
 *
 * Each theme must have a corresponding Nexo font glyph defined in
 * the resource pack glyphs configuration and the texture at
 * assets/minecraft/textures/gui/[theme/]guild_menu_[theme]_[rows]_row.png
 *
 * Only [SELECTABLE] styles are offered; the older themes are kept for stored-value
 * compatibility and resolve to [DEFAULT] (see [resolved]).
 */
enum class GuiTheme(val displayName: String, val hasBackground: Boolean = true) {
    NEUTRAL("Default"),
    EMBERSTONE("Emberstone"),
    CARVED_SLATE("Carved Slate"),
    MOSSBOUND("Mossbound"),
    LAVENDER_HALL("Lavender Hall"),
    IRON_ROSE("Iron Rose"),
    ENTHUSIA("Enthusia"),
    FROSTBOUND("Frostbound"),
    VERDANT("Verdant"),
    VOIDLIGHT("Voidlight"),
    OBSIDIAN("Obsidian"),

    /** Plain vanilla chest: no background glyph and vanilla item icons, for guilds that prefer it. */
    VANILLA("Vanilla", hasBackground = false);

    /**
     * The style actually drawn. The six pre-Enthusia themes stay in the enum so stored values still
     * load, but they are no longer offered and render with the Enthusia background.
     */
    fun resolved(): GuiTheme = if (this in SELECTABLE) this else DEFAULT

    companion object {
        /** Styles offered in the theme picker (Java and Bedrock), in display order. */
        val SELECTABLE: List<GuiTheme> = listOf(ENTHUSIA, FROSTBOUND, VERDANT, VOIDLIGHT, OBSIDIAN, VANILLA)

        /** Style for new guilds and for unknown or retired stored values. */
        val DEFAULT: GuiTheme get() = ENTHUSIA

        /** Maps the database/storage string back to an enum value. */
        fun fromKey(key: String): GuiTheme =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: DEFAULT
    }
}