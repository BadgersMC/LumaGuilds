package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.BankDepositXpCompletion
import java.util.UUID

/** Retained delivery decisions are independent of the prunable XP audit history. */
interface BankDepositXpRepository {
    /** Unknown/legacy IDs return null; a later settlement waits for the guild's earlier READY receipt. */
    fun complete(transactionId: UUID): BankDepositXpCompletion?

    /** At most one READY head per guild, ordered fairly by the last attempt. */
    fun pending(limit: Int): List<UUID>

    /** Separate bounded queue: a cache failure cannot starve XP delivery. */
    fun pendingCache(limit: Int): List<UUID>

    /** Acknowledge this receipt only after the actual cache refresh has succeeded. */
    fun acknowledgeCache(transactionId: UUID)
}
