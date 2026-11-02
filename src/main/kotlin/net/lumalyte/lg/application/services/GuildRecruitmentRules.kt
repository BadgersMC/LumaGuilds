package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.recruitment.RecruitmentPolicy
import java.time.Clock
import java.util.UUID

/** Dynamic policy inputs are read at the same use-case boundaries as before. */
internal class GuildRecruitmentRules(
    val policy: () -> RecruitmentPolicy,
    val eligible: (UUID, UUID) -> Boolean,
    val clock: Clock = Clock.systemUTC(),
) {
    fun requireApplicant(members: MemberService, player: UUID, guild: UUID) {
        require(eligible(player, guild) && members.getPlayerGuilds(player).isEmpty()) {
            "Applicant is no longer eligible"
        }
    }
}
