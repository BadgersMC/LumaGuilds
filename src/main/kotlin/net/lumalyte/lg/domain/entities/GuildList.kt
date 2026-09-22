package net.lumalyte.lg.domain.entities

import java.util.UUID

enum class GuildListSortKey {
    ALL_TIME_ACTIVE,
    WEEKLY_ACTIVE,
    GUILD_LEVEL,
    CREATED_AT;

    val defaultAscending: Boolean
        get() = when (this) {
            ALL_TIME_ACTIVE, WEEKLY_ACTIVE -> false
            GUILD_LEVEL, CREATED_AT -> true
        }
}

data class GuildListRankedRow(
    val guildId: UUID,
    val sortValue: Long,
    val uniquePvpKills: Int = 0,
)
