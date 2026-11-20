package net.lumalyte.lg.domain.values

/** Outcome of a once-only system XP payout (REQ-229). */
enum class SystemAward {
    /** This call applied the payout. */
    PAID_NOW,

    /** The payout ID was applied before; nothing was added. */
    ALREADY_PAID,

    /** Nothing was applied; the caller may retry. */
    FAILED,
}
