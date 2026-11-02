package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.entities.PreparedExperienceAward
import net.lumalyte.lg.domain.values.PeriodWindow
import java.util.UUID

interface BankProgressionRepository {
    /** Commit high-water and the prepared XP decision together; the calculation must be pure. */
    fun awardNetNewUnits(
        guildId: UUID,
        openingBalance: Long,
        currentBalance: Long,
        valuePerUnit: Long,
        window: PeriodWindow,
        prepareAward: (Int) -> PreparedExperienceAward,
    ): ExperienceAwardResult?

    /** Reserve-only compatibility operation; callers awarding XP must use [awardNetNewUnits]. */
    fun reserveNetNewUnits(
        guildId: UUID,
        openingBalance: Long,
        currentBalance: Long,
        valuePerUnit: Long,
        window: PeriodWindow,
    ): Int
}
