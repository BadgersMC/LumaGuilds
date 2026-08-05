package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.GuildStrike
import java.util.UUID

/**
 * Persistence for Guild Strikes — LiteBans punishments attributed to guilds.
 */
interface StrikeRepository {

    /**
     * Records a strike if one with the same [GuildStrike.litebansEntryId] does
     * not already exist (dedupe). Returns true if a new row was inserted.
     */
    fun recordStrike(strike: GuildStrike): Boolean

    /**
     * Marks a strike inactive (punishment removed/expired). Returns true if a row
     * was updated.
     */
    fun deactivateStrike(litebansEntryId: Long): Boolean

    /** Total strikes (active + inactive) recorded against a guild. */
    fun countByGuild(guildId: UUID): Int

    /** All strikes for a guild, newest first. */
    fun getByGuild(guildId: UUID): List<GuildStrike>

    /** Total strikes per guild, guild id -> count. Only guilds with >= 1 strike. */
    fun getAllCounts(): Map<UUID, Int>

    /** Overall number of recorded strikes. */
    fun countAll(): Int
}
