package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

/** Maximum normalized cosmetic category length. */
const val MAX_COSMETIC_TYPE_LENGTH = 32

/** Maximum stable cosmetic key length. */
const val MAX_COSMETIC_KEY_LENGTH = 64

/** Maximum stored display name length. */
const val MAX_COSMETIC_DISPLAY_NAME_LENGTH = 128

/** Maximum stored grant source length. */
const val MAX_COSMETIC_SOURCE_LENGTH = 128

/** Cosmetic type for guild menu background themes ([net.lumalyte.lg.utils.GuiTheme] names). */
const val MENU_THEME_COSMETIC = "MENU_THEME"

// Existing JDK-only plugin API exposes immutable ownership records.

/**
 * A cosmetic a guild owns because an integrating plugin granted it (REQ-121),
 * e.g. a holiday menu theme earned through EnthusiaHolidays. [type] and [key]
 * are stored upper case; unknown values are kept for forward compatibility.
 */
@Suppress("ForbiddenPublicDataClass", "LibraryEntitiesShouldNotBePublic")
data class GuildCosmeticUnlock(
    /** Owning guild identity. */
    val guildId: UUID,
    /** Normalized cosmetic category. */
    val type: String,
    /** Normalized stable cosmetic key. */
    val key: String,
    /** Granted display label. */
    val displayName: String,
    /** Integrating plugin grant reference. */
    val source: String,
    /** Original durable grant time. */
    val unlockedAt: Instant,
)
