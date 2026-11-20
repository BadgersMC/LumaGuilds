package net.lumalyte.lg.application.persistence

import java.time.Instant
import java.util.UUID

/** Durable record of which member/prestige credits were delivered (REQ-154). */
interface PrestigeCreditLedger {
    /** Prestige transaction IDs already credited to [playerId]. */
    fun delivered(playerId: UUID): Set<UUID>

    /** Records one delivery; returns false when the pair was already recorded. */
    fun record(playerId: UUID, transactionId: UUID, at: Instant): Boolean

    /** REQ-187: forgets every member's delivery of these prestiges; returns rows removed. */
    fun forget(transactionIds: Set<UUID>): Int = 0
}
