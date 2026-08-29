package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.BankProgressionRepository
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.values.ExperienceSource
import java.time.Instant
import java.util.UUID

class ChapterTwoGuildAwardService(
    private val bankProgressionRepository: BankProgressionRepository,
    private val membershipHistoryRepository: MembershipHistoryRepository,
    private val permanentExperienceService: PermanentExperienceService,
    private val configService: ConfigService,
    private val playtimeActivityService: PlaytimeActivityService,
) {
    fun awardBankGrowth(
        guildId: UUID,
        actorId: UUID,
        currentBalance: Long,
        occurredAt: Instant = Instant.now(),
    ): ExperienceAwardResult? {
        if (playtimeActivityService.isXpBlocked(actorId)) return null
        val policy = policy(ExperienceSource.BANK_DEPOSIT)
        val window = policy.windowContaining(occurredAt) ?: return null
        val units = bankProgressionRepository.reserveNetNewUnits(
            guildId = guildId,
            currentBalance = currentBalance,
            valuePerUnit = BANK_VALUE_PER_UNIT,
            window = window,
        )
        if (units <= 0) return null

        return permanentExperienceService.award(
            ExperienceAwardRequest(guildId, actorId, ExperienceSource.BANK_DEPOSIT, units, occurredAt),
            policy,
        )
    }

    fun processQualifiedRecruits(occurredAt: Instant = Instant.now()): Int {
        val policy = policy(ExperienceSource.QUALIFIED_RECRUIT)
        val cutoff = occurredAt.minus(ChapterTwoGuildAwardRules.recruitRetention)
        var awarded = 0
        membershipHistoryRepository.getQualifiedUnawarded(cutoff).forEach { stint ->
            val result = permanentExperienceService.award(
                ExperienceAwardRequest(
                    guildId = stint.guildId,
                    actorId = null,
                    source = ExperienceSource.QUALIFIED_RECRUIT,
                    units = 1,
                    occurredAt = occurredAt,
                ),
                policy,
            )
            if (result is ExperienceAwardResult.Awarded &&
                membershipHistoryRepository.markRecruitXpAwarded(stint.id, occurredAt)
            ) {
                awarded++
            }
        }
        return awarded
    }

    fun awardPreCapWarWin(
        guildId: UUID,
        permanentLevel: Int,
        occurredAt: Instant = Instant.now(),
    ): ExperienceAwardResult? {
        if (!ChapterTwoGuildAwardRules.warWinQualifies(permanentLevel)) return null
        val source = ExperienceSource.PRE_CAP_WAR_WIN
        return permanentExperienceService.award(
            ExperienceAwardRequest(guildId, null, source, 1, occurredAt),
            policy(source),
        )
    }

    private fun policy(source: ExperienceSource) =
        configService.loadConfig().progression.sourcePolicies.getValue(source)

    private companion object {
        const val BANK_VALUE_PER_UNIT = 100L
    }
}
