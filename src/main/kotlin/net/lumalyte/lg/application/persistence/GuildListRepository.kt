package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.GuildListRankedRow
import net.lumalyte.lg.domain.entities.GuildListSortKey
import java.time.Instant

interface GuildListRepository {
    /** Returns bounded public ownership/alliance facts for one directory page. */
    fun getDetails(guildIds: Set<java.util.UUID>): Map<java.util.UUID, net.lumalyte.lg.domain.entities.GuildDirectoryDetails> = emptyMap()

    fun getCount(): Int

    fun getPage(
        offset: Int,
        limit: Int,
        sortKey: GuildListSortKey,
        ascending: Boolean,
        weeklyStart: Instant,
        claimsEnabled: Boolean,
        uniqueKillWeight: Int,
    ): List<GuildListRankedRow>
}
