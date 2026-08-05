package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

/**
 * A single recorded strike against a guild, derived from a LiteBans punishment
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
    /** LiteBans punishment id — used to dedupe sync/re-fire events. */
    val litebansEntryId: Long? = null,
    /** False once the punishment is removed/expired (appealed, unmuted, pardoned...). */
    val active: Boolean = true
)
