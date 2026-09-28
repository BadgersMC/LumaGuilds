package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.rewards.PrestigeQuote
import net.lumalyte.lg.domain.rewards.PrestigeRejection
import net.lumalyte.lg.domain.rewards.PrestigeResult

fun interface PrestigeRepository {
    fun confirm(request: PrestigeQuote, guard: () -> PrestigeRejection?): PrestigeResult
}