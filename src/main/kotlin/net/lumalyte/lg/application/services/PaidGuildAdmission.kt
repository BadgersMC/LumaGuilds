package net.lumalyte.lg.application.services

/** Admission is authorized by recruitment eligibility, not an existing member's bank permission. */
interface PaidGuildAdmission {
    fun isEligible(): Boolean

    /** True only after membership has been persisted. False/throw leaves payment pending for review. */
    fun complete(): Boolean
}
