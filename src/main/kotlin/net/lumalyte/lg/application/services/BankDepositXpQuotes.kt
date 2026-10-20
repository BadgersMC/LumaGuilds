package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.BankDepositXpEligibility
import net.lumalyte.lg.domain.entities.BankDepositXpQuote
import net.lumalyte.lg.domain.values.ExperienceSource
import java.time.Instant
import java.util.UUID

/** Platform/config capture is deliberately outside the SQL transaction and external payment. */
class BankDepositXpQuotes(
    private val config: ConfigService,
    private val activity: PlaytimeActivityService,
    private val clock: () -> Instant = { Instant.now() },
) {
    fun capture(actorId: UUID): BankDepositXpQuote {
        val occurredAt = clock()
        val blocked = activity.isXpBlocked(actorId)
        val settings = config.loadConfig().progression
        val policy = settings.sourcePolicies.getValue(ExperienceSource.BANK_DEPOSIT)
        val eligibility = when {
            blocked -> BankDepositXpEligibility.AFK
            !policy.enabled -> BankDepositXpEligibility.DISABLED
            else -> BankDepositXpEligibility.ELIGIBLE
        }
        val boost = settings.xpBoost
        val multiplier = if (eligibility == BankDepositXpEligibility.ELIGIBLE && boost != null &&
            ExperienceSource.BANK_DEPOSIT in boost.sources && occurredAt >= boost.startsAt && occurredAt < boost.endsAt
        ) boost.multiplier else 1.0
        return BankDepositXpQuote(occurredAt, policy, multiplier, eligibility)
    }
}
