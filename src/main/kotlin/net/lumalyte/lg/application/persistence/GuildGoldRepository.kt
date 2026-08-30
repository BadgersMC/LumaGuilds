package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.gold.GuildGoldMutation
import net.lumalyte.lg.domain.gold.GuildGoldOperationRecord
import net.lumalyte.lg.domain.gold.GuildGoldPreparation
import net.lumalyte.lg.domain.gold.GuildGoldResult
import java.util.UUID

interface GuildGoldRepository {
    fun getBalance(guildId: UUID): Long
    fun getTopBalances(limit: Int): List<Pair<UUID, Long>>
    fun getDailyWithdrawn(guildId: UUID, periodStartEpochMs: Long): Long
    fun isFrozen(guildId: UUID): Boolean
    fun setFrozen(guildId: UUID, frozen: Boolean, actorId: UUID, reason: String): Boolean
    fun prepare(mutation: GuildGoldMutation): GuildGoldPreparation
    fun findOperation(transactionId: UUID): GuildGoldOperationRecord?
    fun apply(mutation: GuildGoldMutation, capacity: Long, periodStartEpochMs: Long?): GuildGoldResult
    fun recordCompensation(transactionId: UUID, succeeded: Boolean, details: String): Boolean
}
