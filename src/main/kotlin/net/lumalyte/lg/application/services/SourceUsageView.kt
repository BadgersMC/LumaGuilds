package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.values.CapPeriod
import net.lumalyte.lg.domain.values.ExperienceSource
import java.time.Instant

data class SourceUsageView(
    val source: ExperienceSource,
    val pool: String,
    val period: CapPeriod,
    val awardedXp: Int,
    val capXp: Int?,
    val remainingXp: Int?,
    val resetsAt: Instant?,
)
