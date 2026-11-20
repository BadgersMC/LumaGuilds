package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.PrestigeHistory
import java.time.Instant
import java.util.UUID

/**
 * REQ-172: the prestige history a guild's info view shows. Reading it never
 * stops the view from opening: an unreadable history becomes [Unavailable], and
 * a failing server-first lookup only drops the mark.
 */
sealed interface GuildInfoPrestige {
    data object None : GuildInfoPrestige
    data object Unavailable : GuildInfoPrestige

    data class Entry(val tier: Int, val appliedAt: Instant, val serverFirst: Boolean)

    /** Applied prestiges, oldest first. */
    data class Prestiged(val entries: List<Entry>) : GuildInfoPrestige {
        val current: Int get() = entries.maxOf { it.tier }
    }

    companion object {
        fun of(history: PrestigeHistory, guildId: UUID): GuildInfoPrestige {
            val records = runCatching { history.history(guildId) }.getOrElse { return Unavailable }
            if (records.isEmpty()) return None
            return Prestiged(
                records.sortedBy { it.appliedAt }.map { record ->
                    val first = runCatching { history.firstToReach(record.tier) }.getOrNull()
                    Entry(record.tier, record.appliedAt, serverFirst = first?.transactionId == record.transactionId)
                },
            )
        }
    }
}
