package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.GuildPenalty
import net.lumalyte.lg.domain.entities.PenaltyType
import java.util.UUID

/**
 * Persistence for admin-applied guild penalties (level reduction, EXP
 * reduction, guild mute, disband) — the audit trail behind the strike system.
 */
interface PenaltyRepository {

    /** Records an applied penalty. Returns true on success. */
    fun recordPenalty(penalty: GuildPenalty): Boolean

    /** All penalties applied to a guild, newest first. */
    fun getByGuild(guildId: UUID): List<GuildPenalty>

    /** True when the guild has an in-force GUILD_MUTE penalty at [now]. */
    fun hasActiveMute(guildId: UUID, now: java.time.Instant): Boolean

    /** Most recent penalty applied to a guild, or null. */
    fun getLatest(guildId: UUID): GuildPenalty?
}
