package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.rewards.PrestigeQuote
import net.lumalyte.lg.domain.rewards.PrestigeRejection
import net.lumalyte.lg.domain.rewards.PrestigeResult

fun interface PrestigeRepository {
    fun confirm(request: PrestigeQuote, guard: () -> PrestigeRejection?): PrestigeResult

    /** Older adapters cannot assert that a result is a fresh commit. */
    fun confirmWithCommitStatus(request: PrestigeQuote, guard: () -> PrestigeRejection?): PrestigeConfirmation =
        PrestigeConfirmation(confirm(request, guard))

    /** REQ-186: a staff prestige with [PrestigeQuote.quotedFee] 0 and no level requirement. */
    fun grantWithCommitStatus(request: PrestigeQuote): PrestigeConfirmation =
        PrestigeConfirmation(PrestigeResult.Failed(request.transactionId))
}

/** REQ-185: staff corrections of the prestige count, kept apart from prestige history. */
interface PrestigeAdministration {
    fun setPrestigeCount(
        guildId: java.util.UUID,
        count: Int,
        actorId: java.util.UUID,
        at: java.time.Instant,
    ): net.lumalyte.lg.domain.rewards.PrestigeAdjustmentResult

    fun adjustments(guildId: java.util.UUID): List<net.lumalyte.lg.domain.rewards.PrestigeAdjustment>

    /** The kept rewards a lowering to [count] would drop, newest first; empty if not a lowering. */
    fun rewardsDroppedBy(guildId: java.util.UUID, count: Int): List<String>
}

/** The original result remains identical when read from its durable receipt. */
data class PrestigeConfirmation(val result: PrestigeResult, val newlyCommitted: Boolean = false)
