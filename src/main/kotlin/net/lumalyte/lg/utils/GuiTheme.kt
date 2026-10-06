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
 *
 * Themes with [requiresUnlock] are holiday styles earned through EnthusiaHolidays guild goals
 * (REQ-121); a guild can only apply one after it has been unlocked through the
 * GuildCosmeticUnlocks API. Themes with [seasonalIcons] also swap the menu icons for their
 * `<icon>_<theme>` Nexo variants (see MenuIconAdapter).
 */
enum class GuiTheme(
    val displayName: String,
    val hasBackground: Boolean = true,
    val requiresUnlock: Boolean = false,
    val seasonalIcons: Boolean = false,
) {
    NEUTRAL("Default"),
    EMBERSTONE("Emberstone"),
    CARVED_SLATE("Carved Slate"),
    MOSSBOUND("Mossbound"),
    LAVENDER_HALL("Lavender Hall"),
    IRON_ROSE("Iron Rose"),

    /** Enthusia guild menu style; the default. */
    ENTHUSIA("Enthusia"),

    /** Enthusia layout in the Frostbound palette. */
    FROSTBOUND("Frostbound"),

    /** Enthusia layout in the Verdant palette. */
    VERDANT("Verdant"),

    /** Enthusia layout in the Voidlight palette. */
    VOIDLIGHT("Voidlight"),

    /** Halloween: sculk corruption over a pumpkin-orange frame. Earned in EnthusiaHolidays. */
    HALLOWEEN("Halloween", requiresUnlock = true, seasonalIcons = true),

    /** Christmas: snow, candy-cane bars and holly. Earned in EnthusiaHolidays. */
    CHRISTMAS("Christmas", requiresUnlock = true, seasonalIcons = true),

    /** Enthusia layout in the Obsidian palette. */
    OBSIDIAN("Obsidian"),

    /** Plain vanilla chest: no background glyph and vanilla item icons, for guilds that prefer it. */
    VANILLA("Vanilla", hasBackground = false),
    ;

    /**
     * The style actually drawn. The six pre-Enthusia themes stay in the enum so stored values still
     * load, but they are no longer offered and render with the Enthusia background.
     */
    fun resolved(): GuiTheme = if (this in SELECTABLE) this else DEFAULT

    companion object {
        /** Styles offered in the theme picker (Java and Bedrock), in display order. Locked ones show as locked. */
        val SELECTABLE: List<GuiTheme> =
            listOf(ENTHUSIA, FROSTBOUND, VERDANT, VOIDLIGHT, HALLOWEEN, CHRISTMAS, OBSIDIAN, VANILLA)

        /** Style for new guilds and for unknown or retired stored values. */
        val DEFAULT: GuiTheme get() = ENTHUSIA

        /** Maps the database/storage string back to an enum value. */
        fun fromKey(key: String): GuiTheme =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: DEFAULT
    }
}
