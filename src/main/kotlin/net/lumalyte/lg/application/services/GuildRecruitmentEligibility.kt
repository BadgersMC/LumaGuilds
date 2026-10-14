package net.lumalyte.lg.application.services

import java.util.UUID

/** Platform admission restrictions; unknown storage outcomes must fail closed. */
internal fun interface GuildRecruitmentEligibility {
    /** Tests current player restrictions and personal refusal of the target guild. */
    fun allows(player: UUID, guild: UUID): Boolean
}
