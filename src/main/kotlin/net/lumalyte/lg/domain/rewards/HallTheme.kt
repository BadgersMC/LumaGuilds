package net.lumalyte.lg.domain.rewards

/** REQ-260: one cosmetic slot on a guild's Hall of Prestige statue. */
enum class HallSlot(val label: String) {
    NAMEPLATE("Nameplate"),
    TITLE("Title flourish"),
    BANNER("Banner"),
    POSE("Pose"),
    TRIM("Armor trim"),
    SPOTLIGHT("Spotlight"),
    SOUND("Hall sound"),
    AURA("Aura"),
    PLINTH("Plinth"),
    TROPHY("Trophy"),
}

/**
 * REQ-260: the six Hall of Prestige themes from the run-cosmetics design. A guild earns one theme
 * per prestige, chosen on the prestige screen, and owns every cosmetic in it.
 */
enum class HallTheme(val label: String, val color: Int, private val cosmetics: Map<HallSlot, String>) {
    GILDED("Gilded", 0xE8B528, mapOf(
        HallSlot.NAMEPLATE to "Gilded nameplate", HallSlot.TITLE to "✦ Name ✦", HallSlot.BANNER to "Gold on black, border",
        HallSlot.POSE to "Victory", HallSlot.TRIM to "Wayfinder, gold", HallSlot.SPOTLIGHT to "Gold",
        HallSlot.SOUND to "Bell toll", HallSlot.AURA to "Gold sparkle", HallSlot.PLINTH to "Gilded blackstone",
        HallSlot.TROPHY to "Totem of undying",
    )),
    EMBER("Ember", 0xFF6A2A, mapOf(
        HallSlot.NAMEPLATE to "Ember nameplate", HallSlot.TITLE to "» Name «", HallSlot.BANNER to "Orange on red, flames",
        HallSlot.POSE to "Rally", HallSlot.TRIM to "Rib, redstone", HallSlot.SPOTLIGHT to "Red",
        HallSlot.SOUND to "Fire charge", HallSlot.AURA to "Embers", HallSlot.PLINTH to "Nether bricks",
        HallSlot.TROPHY to "Fire charge",
    )),
    WINGED("Winged", 0xF4F4F4, mapOf(
        HallSlot.NAMEPLATE to "Winged nameplate", HallSlot.TITLE to "❦ Name ❦", HallSlot.BANNER to "White on light grey, wings",
        HallSlot.POSE to "Salute", HallSlot.TRIM to "Flow, quartz", HallSlot.SPOTLIGHT to "White",
        HallSlot.SOUND to "Wing flaps", HallSlot.AURA to "Drifting feathers", HallSlot.PLINTH to "Quartz",
        HallSlot.TROPHY to "Elytra",
    )),
    TIDAL("Tidal", 0x3D7BFF, mapOf(
        HallSlot.NAMEPLATE to "Tidal nameplate", HallSlot.TITLE to "≈ Name ≈", HallSlot.BANNER to "Cyan on blue, waves",
        HallSlot.POSE to "Guard", HallSlot.TRIM to "Tide, lapis", HallSlot.SPOTLIGHT to "Blue",
        HallSlot.SOUND to "Conduit pulse", HallSlot.AURA to "Bubbles", HallSlot.PLINTH to "Prismarine bricks",
        HallSlot.TROPHY to "Heart of the sea",
    )),
    RADIANT("Radiant", 0xFFE27A, mapOf(
        HallSlot.NAMEPLATE to "Radiant nameplate", HallSlot.TITLE to "☀ Name ☀", HallSlot.BANNER to "Yellow on white, sun",
        HallSlot.POSE to "Toast", HallSlot.TRIM to "Raiser, resin", HallSlot.SPOTLIGHT to "Yellow",
        HallSlot.SOUND to "Amethyst chime", HallSlot.AURA to "Glow", HallSlot.PLINTH to "Shroomlight",
        HallSlot.TROPHY to "Beacon",
    )),
    CROWNED("Crowned", 0xA96BFF, mapOf(
        HallSlot.NAMEPLATE to "Crowned nameplate", HallSlot.TITLE to "♛ Name ♛", HallSlot.BANNER to "Gold on purple, crown",
        HallSlot.POSE to "Coronation", HallSlot.TRIM to "Silence, amethyst", HallSlot.SPOTLIGHT to "Purple",
        HallSlot.SOUND to "Enchanting", HallSlot.AURA to "Enchant runes", HallSlot.PLINTH to "Purpur",
        HallSlot.TROPHY to "Nether star",
    )),
    ;

    fun cosmetic(slot: HallSlot): String = cosmetics.getValue(slot)

    companion object {
        fun parse(value: String?): HallTheme? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

/** REQ-260/261: what a guild owns and wears in the Hall. Unset slots wear the first theme the guild earned. */
data class HallDisplay(
    val owned: List<HallTheme>,
    val picksLeft: Int,
    val choices: Map<HallSlot, HallTheme>,
    val skin: java.util.UUID?,
) {
    fun wearing(slot: HallSlot): HallTheme? = choices[slot]?.takeIf { it in owned } ?: owned.firstOrNull()
    val unowned: List<HallTheme> get() = HallTheme.entries.filterNot { it in owned }
}
