package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.Kill
import net.lumalyte.lg.domain.values.CombatSummary
import java.time.Instant
import java.util.UUID

/** REQ-202: one guild this guild has fought, with kills against it and deaths from it. */
data class GuildRival(val guildId: UUID, val kills: Int, val deaths: Int)

/** REQ-199 to REQ-202: saved player kills. Reads throw when the database cannot be read. */
interface GuildKillLog {
    fun record(kill: Kill)

    /** When [killerId] last killed [victimId], or null. */
    fun lastKill(killerId: UUID, victimId: UUID): Instant?

    /** The guild's kills and deaths in [from, to); null bounds are open. */
    fun guildSummary(guildId: UUID, from: Instant? = null, to: Instant? = null): CombatSummary

    fun playerSummary(playerId: UUID): CombatSummary

    /** Newest first, kills and deaths involving the guild. */
    fun recent(guildId: UUID, limit: Int): List<Kill>

    /** Newest first, kills either way between two guilds. */
    fun between(guildA: UUID, guildB: UUID, limit: Int): List<Kill>

    /** Kills by each of [players], all time; players with none are left out. */
    fun killCounts(players: Collection<UUID>): Map<UUID, Int>

    /** The guilds fought most, by kills plus deaths, largest first. */
    fun rivals(guildId: UUID, limit: Int): List<GuildRival>
}
