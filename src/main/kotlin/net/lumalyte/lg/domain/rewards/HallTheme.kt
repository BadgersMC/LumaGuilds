package net.lumalyte.lg.domain.rewards

/**
 * REQ-260: one themed cosmetic slot on a guild's Hall of Prestige statue. The banner is the guild's own
 * banner and the armor is the guild's own choice (REQ-270, REQ-271), so neither is a theme slot.
 */
enum class HallSlot(val label: String) {
    NAMEPLATE("Nameplate"),
    TITLE("Title flourish"),
    POSE("Pose"),
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
        HallSlot.NAMEPLATE to "Gilded nameplate", HallSlot.TITLE to "✦ Name ✦",
        HallSlot.POSE to "Victory", HallSlot.SPOTLIGHT to "Gold",
        HallSlot.SOUND to "Bell toll", HallSlot.AURA to "Gold sparkle", HallSlot.PLINTH to "Gilded blackstone",
        HallSlot.TROPHY to "Totem of undying",
    )),
    EMBER("Ember", 0xFF6A2A, mapOf(
        HallSlot.NAMEPLATE to "Ember nameplate", HallSlot.TITLE to "» Name «",
        HallSlot.POSE to "Rally", HallSlot.SPOTLIGHT to "Red",
        HallSlot.SOUND to "Fire charge", HallSlot.AURA to "Embers", HallSlot.PLINTH to "Nether bricks",
        HallSlot.TROPHY to "Fire charge",
    )),
    WINGED("Winged", 0xF4F4F4, mapOf(
        HallSlot.NAMEPLATE to "Winged nameplate", HallSlot.TITLE to "❦ Name ❦",
        HallSlot.POSE to "Salute", HallSlot.SPOTLIGHT to "White",
        HallSlot.SOUND to "Wing flaps", HallSlot.AURA to "Drifting feathers", HallSlot.PLINTH to "Quartz",
        HallSlot.TROPHY to "Elytra",
    )),
    TIDAL("Tidal", 0x3D7BFF, mapOf(
        HallSlot.NAMEPLATE to "Tidal nameplate", HallSlot.TITLE to "≈ Name ≈",
        HallSlot.POSE to "Guard", HallSlot.SPOTLIGHT to "Blue",
        HallSlot.SOUND to "Conduit pulse", HallSlot.AURA to "Bubbles", HallSlot.PLINTH to "Prismarine bricks",
        HallSlot.TROPHY to "Heart of the sea",
    )),
    RADIANT("Radiant", 0xFFE27A, mapOf(
        HallSlot.NAMEPLATE to "Radiant nameplate", HallSlot.TITLE to "☀ Name ☀",
        HallSlot.POSE to "Toast", HallSlot.SPOTLIGHT to "Yellow",
        HallSlot.SOUND to "Amethyst chime", HallSlot.AURA to "Glow", HallSlot.PLINTH to "Shroomlight",
        HallSlot.TROPHY to "Beacon",
    )),
    CROWNED("Crowned", 0xA96BFF, mapOf(
        HallSlot.NAMEPLATE to "Crowned nameplate", HallSlot.TITLE to "♛ Name ♛",
        HallSlot.POSE to "Coronation", HallSlot.SPOTLIGHT to "Purple",
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
    val armor: Map<HallArmorPart, HallArmorPiece> = emptyMap(),
) {
    fun wearing(slot: HallSlot): HallTheme? = choices[slot]?.takeIf { it in owned } ?: owned.firstOrNull()
    val unowned: List<HallTheme> get() = HallTheme.entries.filterNot { it in owned }
}

/** REQ-271: the four armor pieces on the Hall statue. */
enum class HallArmorPart(val label: String) {
    HEAD("Helmet"), CHEST("Chestplate"), LEGS("Leggings"), FEET("Boots"),
}

/** REQ-271: vanilla armor materials a guild can dress its statue in; no prestige limits. */
enum class HallArmorMaterial(val label: String, val dyeable: Boolean = false) {
    LEATHER("Leather", dyeable = true), CHAINMAIL("Chainmail"), COPPER("Copper"), IRON("Iron"),
    GOLDEN("Gold"), DIAMOND("Diamond"), NETHERITE("Netherite"),
    ;

    companion object {
        fun parse(value: String?): HallArmorMaterial? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }

        /** The statue's armor before a guild picks any: by prestige tier, as the Hall plan had it. */
        fun forTier(tier: Int): HallArmorMaterial = when {
            tier >= 6 -> NETHERITE
            tier == 5 -> GOLDEN
            tier >= 3 -> DIAMOND
            else -> IRON
        }
    }
}

/**
 * REQ-271: one statue armor piece: material, optional trim (vanilla registry keys such as
 * "wayfinder" and "gold") and, for leather, an RGB dye. Stored as one short text value.
 */
data class HallArmorPiece(
    val material: HallArmorMaterial,
    val trimPattern: String? = null,
    val trimMaterial: String? = null,
    val dye: Int? = null,
) {
    init {
        require(trimPattern == null || KEY.matches(trimPattern)) { "bad trim pattern" }
        require(trimMaterial == null || KEY.matches(trimMaterial)) { "bad trim material" }
        require((trimPattern == null) == (trimMaterial == null)) { "a trim needs a pattern and a material" }
        require(dye == null || (material.dyeable && dye in 0..0xFFFFFF)) { "only leather takes a dye" }
    }

    fun encode(): String = listOf(material.name, trimPattern.orEmpty(), trimMaterial.orEmpty(),
        dye?.let { "%06x".format(it) }.orEmpty()).joinToString(";")

    companion object {
        private val KEY = Regex("[a-z0-9_]{1,24}")

        fun decode(value: String?): HallArmorPiece? = runCatching {
            val parts = value!!.split(";")
            val material = HallArmorMaterial.parse(parts[0])!!
            HallArmorPiece(material, parts.getOrNull(1)?.ifEmpty { null }, parts.getOrNull(2)?.ifEmpty { null },
                parts.getOrNull(3)?.ifEmpty { null }?.toInt(16)?.takeIf { material.dyeable })
        }.getOrNull()
    }
}
