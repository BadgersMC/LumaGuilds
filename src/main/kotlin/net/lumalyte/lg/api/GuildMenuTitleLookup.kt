package net.lumalyte.lg.api

import java.util.UUID

/** Optional presentation service for a companion's Guilds-linked chest menu. */
fun interface GuildMenuTitleLookup {
    /** Returns null when the companion should display its plain title. */
    fun title(guildId: UUID, viewerId: UUID, rows: Int, plainTitle: String): String?
}
