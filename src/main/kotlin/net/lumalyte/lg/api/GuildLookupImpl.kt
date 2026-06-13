// Must be public (registered in Bukkit's ServicesManager and consumed by other
// plugins); it mirrors the GuildLookup interface so the function count and the
// guard-clause return counts are inherent to that contract.
@file:Suppress("LibraryEntitiesShouldNotBePublic", "TooManyFunctions", "ReturnCount")

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

    override fun getPlayerGuildIds(playerId: UUID): Set<UUID> = members.getPlayerGuilds(playerId)

    override fun getGuild(guildId: UUID): GuildSummary? = guilds.getGuild(guildId)?.toSummary()

    override fun getAllGuilds(): List<GuildSummary> = guilds.getAllGuilds().map { it.toSummary() }

    override fun isMember(playerId: UUID, guildId: UUID): Boolean = members.getMember(playerId, guildId) != null

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

    override fun getBankBalance(guildId: UUID): Long = banks.getBalance(guildId).toLong()

    override fun bankWithdraw(guildId: UUID, actorId: UUID, amount: Long, reason: String): Boolean {
        val intAmount = amount.toIntBankAmountOrNull() ?: return false
        return banks.withdraw(guildId, actorId, intAmount, reason) != null
    }

    override fun bankDeposit(guildId: UUID, actorId: UUID, amount: Long, reason: String): Boolean {
        val intAmount = amount.toIntBankAmountOrNull() ?: return false
        return banks.deposit(guildId, actorId, intAmount, reason) != null
    }

    private fun Long.toIntBankAmountOrNull(): Int? = if (this <= 0 || this > Int.MAX_VALUE) null else toInt()

    private fun Guild.toSummary() = GuildSummary(id, name, tag, emoji)
}
