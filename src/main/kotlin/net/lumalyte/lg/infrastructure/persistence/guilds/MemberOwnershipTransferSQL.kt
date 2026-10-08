package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.Connection
import java.util.UUID

/** Compare-and-set both ranks in one transaction; failures preserve the current owner. */
internal class MemberOwnershipTransferSQL(private val storage: Storage<Database>) {
    fun transfer(current: Member, next: Member, demotedRank: UUID): Boolean =
        try {
            storage.connection.connection.use { connection ->
                connection.committingTransaction {
                    if (!updatePair(connection, current, next, demotedRank)) throw TransferRejected()
                    true
                }
            }
        } catch (_: TransferRejected) {
            false
        }

    private fun updatePair(c: Connection, current: Member, next: Member, demotedRank: UUID): Boolean {
        val ranks = RankPair(current.rankId, demotedRank)
        return replace(c, Change(current, demotedRank, ranks)) && replace(c, Change(next, current.rankId, ranks))
    }

    private fun replace(c: Connection, change: Change): Boolean {
        val sql = "UPDATE members SET rank_id = ? WHERE player_id = ? AND guild_id = ? AND rank_id = ? " +
            "AND EXISTS (SELECT 1 FROM ranks WHERE id = ? AND guild_id = ? AND priority = 0) " +
            "AND EXISTS (SELECT 1 FROM ranks WHERE id = ? AND guild_id = ? AND priority > 0)"
        return c.prepareStatement(sql).use { statement ->
            val args = listOf(change.destination, change.member.playerId, change.member.guildId, change.member.rankId,
                change.ranks.owner, change.member.guildId, change.ranks.demoted, change.member.guildId)
            args.forEachIndexed { index, value -> statement.setString(index + 1, value.toString()) }
            statement.executeUpdate() == 1
        }
    }

    private class TransferRejected : RuntimeException()
    private data class RankPair(val owner: UUID, val demoted: UUID)
    private data class Change(val member: Member, val destination: UUID, val ranks: RankPair)
}
