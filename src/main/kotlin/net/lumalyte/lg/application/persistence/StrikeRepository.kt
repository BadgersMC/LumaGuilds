package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.GuildStrike
import java.util.UUID

/**
 * Persistence for Guild Strikes — LiteBans punishments attributed to guilds.
 */
interface StrikeRepository {

    /**
     * Records a strike if one with the same [GuildStrike.punishmentType] +
     * [GuildStrike.litebansEntryId] does not already exist (dedupe). Returns
     * true if a new row was inserted. LiteBans ids are per-table sequences, so
     * the type is part of the dedupe key.
     */
    fun recordStrike(strike: GuildStrike): Boolean

    /**
     * Marks a strike inactive (punishment removed/expired). Returns true if a row
     * was updated. [punishmentType] disambiguates LiteBans' per-table id sequences.
     */
    fun deactivateStrike(punishmentType: String, litebansEntryId: Long): Boolean

    /** Total strikes (active + inactive) recorded against a guild. */
    fun countByGuild(guildId: UUID): Int

    /** Strikes currently in force (active = 1) against a guild. */
    fun countActiveByGuild(guildId: UUID): Int

    /** All strikes for a guild, newest first. */
    fun getByGuild(guildId: UUID): List<GuildStrike>

    /** Total strikes per guild, guild id -> count. Only guilds with >= 1 strike. */
    fun getAllCounts(): Map<UUID, Int>

    /** Active (in-force) strikes per guild, guild id -> count. Only guilds with >= 1. */
    fun getAllActiveCounts(): Map<UUID, Int>

    /** Overall number of recorded strikes. */
    fun countAll(): Int
}
