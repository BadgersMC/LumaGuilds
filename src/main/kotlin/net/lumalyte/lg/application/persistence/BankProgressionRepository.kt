package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.values.PeriodWindow
import java.util.UUID

interface BankProgressionRepository {
    fun reserveNetNewUnits(
        guildId: UUID,
        currentBalance: Long,
        valuePerUnit: Long,
        window: PeriodWindow,
    ): Int
}
