package net.lumalyte.lg.api

import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import java.util.UUID

/**
 * [GuildLookup] backed by LumaGuilds' internal application services. Constructed
 * and registered in Bukkit's ServicesManager by the plugin at enable, so all the
 * Koin resolution happens inside LumaGuilds' own classloader (safe) and consumers
 * only ever touch the [GuildLookup] interface.
 */
class GuildLookupImpl(
    private val guilds: GuildService,
    private val members: MemberService,
    private val ranks: RankService,
    private val banks: BankService,
) : GuildLookup {

    override fun getPlayerGuildIds(playerId: UUID): Set<UUID> =
        members.getPlayerGuilds(playerId)

    override fun getGuild(guildId: UUID): GuildSummary? =
        guilds.getGuild(guildId)?.toSummary()

    override fun getAllGuilds(): List<GuildSummary> =
        guilds.getAllGuilds().map { it.toSummary() }

    override fun isMember(playerId: UUID, guildId: UUID): Boolean =
        members.getMember(playerId, guildId) != null

    override fun hasShopPermission(playerId: UUID, guildId: UUID, permission: String): Boolean {
        val perm = try {
            RankPermission.valueOf(permission)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return try {
            members.hasPermission(playerId, guildId, perm)
        } catch (_: Exception) {
            false
        }
    }

    override fun hasRankAtLeast(playerId: UUID, guildId: UUID, rankName: String): Boolean {
        val playerRankId = members.getPlayerRankId(playerId, guildId) ?: return false
        val guildRanks = ranks.listRanks(guildId)
        val playerRank = guildRanks.find { it.id == playerRankId } ?: return false
        val targetRank = guildRanks.find { it.name.equals(rankName, ignoreCase = true) } ?: return false
        // Lower priority number = higher rank (0 = Owner).
        return playerRank.priority <= targetRank.priority
    }

    override fun getBankBalance(guildId: UUID): Long =
        banks.getBalance(guildId).toLong()

    override fun bankWithdraw(guildId: UUID, actorId: UUID, amount: Int, reason: String): Boolean =
        banks.withdraw(guildId, actorId, amount, reason) != null

    override fun bankDeposit(guildId: UUID, actorId: UUID, amount: Int, reason: String): Boolean =
        banks.deposit(guildId, actorId, amount, reason) != null

    private fun Guild.toSummary() = GuildSummary(id, name, tag, emoji)
}
