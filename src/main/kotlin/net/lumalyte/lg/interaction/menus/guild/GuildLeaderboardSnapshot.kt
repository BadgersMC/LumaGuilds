package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.domain.entities.Guild
import org.bukkit.Bukkit
import org.bukkit.entity.Player

internal data class GuildLeaderboardEntry(val guild: Guild, val rank: Int, val score: Int, val members: Int)
internal data class GuildLeaderboardSnapshot(val page: Int, val pages: Int, val rows: List<GuildLeaderboardEntry>)

/** Both native presentations share sorting, page bounds, and viewer-visible online counts. */
internal class GuildLeaderboardSource(
    private val guilds: GuildService,
    private val members: MemberService,
    private val quests: QuestService,
) {
    fun snapshot(player: Player, tab: Int, requestedPage: Int): GuildLeaderboardSnapshot {
        val all = guilds.getAllGuilds()
        val wins = if (tab == 1) quests.recordedWins() else emptyMap()
        val scores = all.associate { guild -> guild.id to when (tab) {
            0 -> guild.level
            1 -> wins[guild.id] ?: 0
            else -> members.getGuildMembers(guild.id).count { member ->
                Bukkit.getPlayer(member.playerId)?.let { it.isOnline && player.canSee(it) } == true
            }
        } }
        val sorted = all.sortedWith(compareByDescending<Guild> { scores[it.id] }
            .thenBy { it.name.lowercase() }.thenBy { it.id.toString() })
        val pages = maxOf(1, (sorted.size + 3) / 4)
        val page = requestedPage.coerceIn(0, pages - 1)
        return GuildLeaderboardSnapshot(page, pages, sorted.drop(page * 4).take(4).mapIndexed { index, guild ->
            GuildLeaderboardEntry(guild, page * 4 + index + 1, scores.getValue(guild.id), members.getMemberCount(guild.id))
        })
    }
}
