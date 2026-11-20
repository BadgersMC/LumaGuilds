package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.domain.values.AllyHomeAccess
import java.util.UUID

/**
 * REQ-180, REQ-182: a guild's second allied home and its allied-home access
 * rules. Reads come from memory; writes go to storage first and return false
 * when it failed, leaving the cached state unchanged.
 */
interface AllyHomeAccessRepository {
    fun access(hostGuildId: UUID): AllyHomeAccess

    /** `null` clears the pick, so every rank of that ally is admitted again. */
    fun setVisitorRanks(hostGuildId: UUID, visitorGuildId: UUID, rankIds: Set<UUID>?): Boolean

    fun setBlocked(hostGuildId: UUID, playerId: UUID, blocked: Boolean): Boolean

    /** REQ-183: per ally, the guild's own ranks that may visit it. */
    fun outboundRanks(guildId: UUID): Map<UUID, Set<UUID>>

    /** `null` clears the pick, so rank permissions decide again. */
    fun setOutboundRanks(guildId: UUID, allyGuildId: UUID, rankIds: Set<UUID>?): Boolean

    fun secondHome(guildId: UUID): GuildHome?

    fun setSecondHome(guildId: UUID, home: GuildHome?): Boolean

    /** Removes everything the guild hosts or picked (disband). */
    fun deleteGuild(guildId: UUID)
}
