package net.lumalyte.lg.domain.values

/**
 * Represents banner design data submitted by a guild.
 */
data class BannerDesignData(
    val baseColor: BannerColor,
    val patterns: List<BannerPattern> = emptyList()
) {
    /**
     * Validates the banner design data.
     */
    fun isValid(): Boolean {
        return patterns.size <= 6 && // Minecraft banner limit
               patterns.all { it.isValid() }
    }
}

/**
 * Represents a banner pattern.
 */
data class BannerPattern(
    val type: String, // e.g., "STRIPE_TOP", "CROSS", "BORDER"
    val color: BannerColor
) {
    fun isValid(): Boolean = type.isNotBlank() && color.isValid()
}

/**
 * Represents a banner color.
 */
enum class BannerColor(val displayName: String, val hexCode: String, val materialName: String) {
    WHITE("White", "#FFFFFF", "WHITE"),
    ORANGE("Orange", "#FF8F00", "ORANGE"),
    MAGENTA("Magenta", "#C74EBD", "MAGENTA"),
    LIGHT_BLUE("Light Blue", "#3AAFD9", "LIGHT_BLUE"),
    YELLOW("Yellow", "#FED83D", "YELLOW"),
    LIME("Lime", "#80C71F", "LIME"),
    PINK("Pink", "#F38BAA", "PINK"),
    GRAY("Gray", "#474F52", "GRAY"),
    LIGHT_GRAY("Light Gray", "#9D9D97", "LIGHT_GRAY"),
    CYAN("Cyan", "#169C9C", "CYAN"),
    PURPLE("Purple", "#9932CC", "PURPLE"),
    BLUE("Blue", "#3C44AA", "BLUE"),
    BROWN("Brown", "#825432", "BROWN"),
    GREEN("Green", "#5E7C16", "GREEN"),
    RED("Red", "#B02E26", "RED"),
    BLACK("Black", "#1D1C21", "BLACK");

    fun isValid(): Boolean = true
}
