package net.lumalyte.lg.utils

/**
 * GUI background themes for guild menus.
 *
 * Each theme must have a corresponding Nexo font glyph defined in
 * the resource pack glyphs configuration and the texture at
 * assets/minecraft/textures/gui/[theme/]guild_menu_[theme]_[rows]_row.png
 *
 * The NEUTRAL theme always has textures available; other themes are
 * tiered content that guilds unlock through progression.
 */
enum class GuiTheme(val displayName: String) {
    NEUTRAL("Default"),
    EMBERSTONE("Emberstone"),
    CARVED_SLATE("Carved Slate"),
    MOSSBOUND("Mossbound"),
    LAVENDER_HALL("Lavender Hall"),
    IRON_ROSE("Iron Rose");

    companion object {
        /** Maps the database/storage string back to an enum value. */
        fun fromKey(key: String): GuiTheme =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: NEUTRAL
    }
}