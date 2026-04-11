package net.lumalyte.lg.domain.events

import net.lumalyte.lg.domain.entities.ExtendedLeaderboardType
import net.lumalyte.lg.domain.entities.LeaderboardPeriod
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * Fired when a guild's rank changes on any leaderboard.
 *
 * @property guildId The guild whose rank changed.
 * @property leaderboardType Which leaderboard changed.
 * @property period The time period of the leaderboard.
 * @property oldRank The previous rank (null if newly entered).
 * @property newRank The new rank.
 */
class GuildLeaderboardRankChangeEvent(
    val guildId: UUID,
    val leaderboardType: ExtendedLeaderboardType,
    val period: LeaderboardPeriod,
    val oldRank: Int?,
    val newRank: Int
) : Event() {

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
