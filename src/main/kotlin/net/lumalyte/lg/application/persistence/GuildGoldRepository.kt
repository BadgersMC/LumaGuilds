package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.gold.GuildGoldMutation
import net.lumalyte.lg.domain.gold.GuildGoldOperationRecord
import net.lumalyte.lg.domain.gold.GuildGoldPreparation
import net.lumalyte.lg.domain.gold.GuildGoldResult
import java.util.UUID

interface GuildGoldRepository {
    /** Persist permission to start an external effect; false means do not call the provider. */
    fun beginExternal(transactionId: UUID, purpose: String): Boolean
    fun recordExternalOutcome(transactionId: UUID, applied: Boolean): Boolean
    fun reconcileUnstarted(createdBeforeEpochMs: Long): Int
    fun confirmedCredits(): List<GuildGoldOperationRecord>
    fun pendingPhysicalCredits(): List<GuildGoldOperationRecord>
    fun recoverConfirmedCredit(transactionId: UUID, capacity: Long): GuildGoldResult
    /** Reverse only a held external credit; retain the guard until item restoration is confirmed. */
    fun reverseExternalCredit(transactionId: UUID): Boolean
    fun getBalance(guildId: UUID): Long
    fun getTopBalances(limit: Int): List<Pair<UUID, Long>>
    fun getDailyWithdrawn(guildId: UUID, periodStartEpochMs: Long): Long
    fun isFrozen(guildId: UUID): Boolean
    fun setFrozen(guildId: UUID, frozen: Boolean, actorId: UUID, reason: String): Boolean
    fun prepare(mutation: GuildGoldMutation): GuildGoldPreparation
    fun findOperation(transactionId: UUID): GuildGoldOperationRecord?
    fun apply(mutation: GuildGoldMutation, capacity: Long, periodStartEpochMs: Long?): GuildGoldResult
    fun applyExternalDebit(mutation: GuildGoldMutation, capacity: Long, periodStartEpochMs: Long): GuildGoldResult
    /** Apply a paid admission credit while retaining the unfinished external-operation guard. */
    fun applyExternalCredit(mutation: GuildGoldMutation, capacity: Long): GuildGoldResult
    fun completeExternal(transactionId: UUID): Boolean
    fun rejectPrepared(transactionId: UUID, reason: net.lumalyte.lg.domain.gold.GuildGoldRejection): Boolean
    fun compensateDebit(
        originalTransactionId: UUID,
        compensation: GuildGoldMutation,
        capacity: Long,
        periodStartEpochMs: Long,
        details: String
    ): GuildGoldResult
    fun recordCompensation(transactionId: UUID, succeeded: Boolean, details: String): Boolean
}
