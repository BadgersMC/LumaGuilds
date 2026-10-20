package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.BankDepositXpRepository
import net.lumalyte.lg.domain.entities.BankDepositXpCompletion
import net.lumalyte.lg.domain.entities.BankDepositXpStatus
import java.util.UUID

/** Bounded delivery and cache retry. Neither path calls a money provider or replays deposit events. */
class BankDepositXpDeliveryService(
    private val repository: BankDepositXpRepository,
    private val publish: (UUID, Int?, () -> Unit) -> Unit = { _, _, _ -> },
    private val onFailure: (UUID, Throwable) -> Unit = { _, _ -> },
) {
    fun deliver(transactionId: UUID): BankDepositXpCompletion? {
        val result = repository.complete(transactionId) ?: return null
        if (result.status == BankDepositXpStatus.AWARDED) {
            // Level effects are best effort on the first committed completion only. A repeated
            // receipt repairs caches, including after a process restart, without repeating them.
            runCatching {
                publish(result.guildId, result.leveledUpTo.takeIf { result.awardedNow }) {
                    repository.acknowledgeCache(transactionId)
                }
            }.onFailure { onFailure(transactionId, it) }
        }
        return result
    }

    fun drain(limit: Int = 64): Int = attempt(repository.pending(limit))

    fun retryCache(limit: Int = 64): Int = attempt(repository.pendingCache(limit))

    private fun attempt(ids: List<UUID>): Int {
        var completed = 0
        ids.forEach { id ->
            runCatching { deliver(id) }.onSuccess { result ->
                if (result != null && result.status !in setOf(
                    BankDepositXpStatus.PREPARED, BankDepositXpStatus.READY, BankDepositXpStatus.WAITING,
                )) completed++
            }.onFailure { onFailure(id, it) }
        }
        return completed
    }
}
