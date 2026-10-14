package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.LfgJoinResult
import net.lumalyte.lg.domain.entities.Guild
import java.util.UUID

internal const val RECRUITMENT_CHANGED_MESSAGE = "Guild recruitment changed. Please reopen the guild browser"

/** A checked guild and default rank, or the precise canonical admission denial. */
internal sealed interface LfgAdmissionCheck {
    data class Ready(val guild: Guild, val rank: UUID) : LfgAdmissionCheck
    data class Denied(val result: LfgJoinResult) : LfgAdmissionCheck
}
