package net.lumalyte.lg.application.persistence

/** Database certainty for one completed synchronous write attempt; UNKNOWN never means rollback. */
internal enum class InviteBlockWriteOutcome { COMMITTED, NOT_APPLIED, UNKNOWN }

/** Keeps a confirmed database commit distinct from cache publication and later connection cleanup. */
internal data class InviteBlockWriteResult(
    val outcome: InviteBlockWriteOutcome,
    val cacheReconciled: Boolean = false,
    val failure: Throwable? = null,
) {
    val succeeded: Boolean get() = outcome == InviteBlockWriteOutcome.COMMITTED && cacheReconciled
}
