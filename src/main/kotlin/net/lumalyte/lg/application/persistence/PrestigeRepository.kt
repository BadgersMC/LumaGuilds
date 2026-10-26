package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.rewards.PrestigeQuote
import net.lumalyte.lg.domain.rewards.PrestigeRejection
import net.lumalyte.lg.domain.rewards.PrestigeResult

fun interface PrestigeRepository {
    fun confirm(request: PrestigeQuote, guard: () -> PrestigeRejection?): PrestigeResult

    /** Older adapters cannot assert that a result is a fresh commit. */
    fun confirmWithCommitStatus(request: PrestigeQuote, guard: () -> PrestigeRejection?): PrestigeConfirmation =
        PrestigeConfirmation(confirm(request, guard))
}

/** The original result remains identical when read from its durable receipt. */
data class PrestigeConfirmation(val result: PrestigeResult, val newlyCommitted: Boolean = false)
