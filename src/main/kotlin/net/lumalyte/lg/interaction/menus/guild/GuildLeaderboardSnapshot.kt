package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.domain.entities.Guild
import org.bukkit.Bukkit
import org.bukkit.entity.Player

internal data class GuildLeaderboardEntry(val guild: Guild, val rank: Int, val score: Int, val members: Int, val prestige: Int = 0)
internal data class GuildLeaderboardSnapshot(val page: Int, val pages: Int, val rows: List<GuildLeaderboardEntry>)

/** Both native presentations share sorting, page bounds, and viewer-visible online counts. */
internal class GuildLeaderboardSource(
    private val guilds: GuildService,
    private val members: MemberService,
    private val quests: QuestService,
    /** REQ-284: each guild's prestige count; guilds that never prestiged are absent. */
    private val prestige: () -> Map<java.util.UUID, Int> = ::prestigeCounts,
) {
    fun snapshot(player: Player, tab: Int, requestedPage: Int): GuildLeaderboardSnapshot {
        val all = guilds.getAllGuilds()
        val tiers = runCatching(prestige).getOrDefault(emptyMap())
        val wins = if (tab == 1) quests.recordedWins() else emptyMap()
        val scores = all.associate { guild -> guild.id to when (tab) {
            0 -> guild.level
            1 -> wins[guild.id] ?: 0
            else -> members.getGuildMembers(guild.id).count { member ->
                Bukkit.getPlayer(member.playerId)?.let { it.isOnline && player.canSee(it) } == true
            }
        } }
        val sorted = all.sortedWith(order(tab, scores, tiers))
        val pages = maxOf(1, (sorted.size + 3) / 4)
        val page = requestedPage.coerceIn(0, pages - 1)
        return GuildLeaderboardSnapshot(page, pages, sorted.drop(page * 4).take(4).mapIndexed { index, guild ->
            GuildLeaderboardEntry(guild, page * 4 + index + 1, scores.getValue(guild.id), members.getMemberCount(guild.id),
                tiers[guild.id] ?: 0)
        })
    }

    /** REQ-285: the guild's place on the level standings (#1 first) and how many guilds there are. */
    fun levelStanding(guildId: java.util.UUID): Pair<Int, Int>? {
        val all = guilds.getAllGuilds()
        val tiers = runCatching(prestige).getOrDefault(emptyMap())
        val sorted = all.sortedWith(order(0, all.associate { it.id to it.level }, tiers))
        val index = sorted.indexOfFirst { it.id == guildId }
        return if (index < 0) null else (index + 1) to sorted.size
    }

    companion object {
        /**
         * REQ-284: on the level standings a prestige outranks any level, since a prestige resets the guild to
         * level 1; then level, then name. Other tabs sort by their own score.
         */
        internal fun order(tab: Int, scores: Map<java.util.UUID, Int>, tiers: Map<java.util.UUID, Int>): Comparator<Guild> {
            val byPrestige = if (tab == 0) compareByDescending<Guild> { tiers[it.id] ?: 0 } else compareBy<Guild> { 0 }
            return byPrestige.thenByDescending { scores[it.id] ?: 0 }.thenBy { it.name.lowercase() }.thenBy { it.id.toString() }
        }

        private fun prestigeCounts(): Map<java.util.UUID, Int> =
            org.koin.core.context.GlobalContext.getOrNull()
                ?.getOrNull<net.lumalyte.lg.application.persistence.PrestigeHistory>()
                ?.standings()?.associate { it.guildId to it.prestige }.orEmpty()
    }
}
