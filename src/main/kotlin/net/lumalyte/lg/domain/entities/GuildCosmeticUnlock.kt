package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

const val MAX_COSMETIC_TYPE_LENGTH = 32
const val MAX_COSMETIC_KEY_LENGTH = 64
const val MAX_COSMETIC_DISPLAY_NAME_LENGTH = 128
const val MAX_COSMETIC_SOURCE_LENGTH = 128

/** Cosmetic type for guild menu background themes ([net.lumalyte.lg.utils.GuiTheme] names). */
const val MENU_THEME_COSMETIC = "MENU_THEME"

/**
 * A cosmetic a guild owns because an integrating plugin granted it (REQ-121),
 * e.g. a holiday menu theme earned through EnthusiaHolidays. [type] and [key]
 * are stored upper case; unknown values are kept for forward compatibility.
 */
data class GuildCosmeticUnlock(
    val guildId: UUID,
    val type: String,
    val key: String,
    val displayName: String,
    val source: String,
    val unlockedAt: Instant,
)
