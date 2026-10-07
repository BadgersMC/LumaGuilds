package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

/**
 * A single recorded strike against a guild, derived from a moderation punishment
 * that was issued to one of the guild's members at the time of the punishment.
 *
 * The strike is attributed to the guild the player belonged to when the
 * punishment was issued — not the guild they may belong to now — so members
 * cannot dodge strikes by leaving the guild.
 */
data class GuildStrike(
    val id: Long = 0,
    val guildId: UUID,
    val playerUuid: UUID,
    val playerName: String? = null,
    val punishmentType: String,
    val reason: String? = null,
    val executorName: String? = null,
    val issuedAt: Instant,
    /** Legacy LiteBans punishment id retained for historical compatibility. */
    val litebansEntryId: Long? = null,
    /** Provider-neutral source for current first-party punishment feeds. */
    val sourceProvider: String? = null,
    /** Stable provider-owned punishment id used for replay dedupe. */
    val sourcePunishmentId: String? = null,
    /** Natural expiration for active mute/ban strikes. */
    val expiresAt: Instant? = null,
    /** False once the punishment is removed/expired. */
    val active: Boolean = true,
)
