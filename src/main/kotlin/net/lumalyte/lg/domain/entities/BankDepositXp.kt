package net.lumalyte.lg.domain.entities

import net.lumalyte.lg.domain.values.ExperiencePolicy
import net.lumalyte.lg.domain.values.ExperienceSource
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

/** Captured before the external payment starts; recovery never consults live activity or configuration. */
data class BankDepositXpQuote(
    val occurredAt: Instant,
    val policy: ExperiencePolicy,
    val boostMultiplier: Double = 1.0,
    val eligibility: BankDepositXpEligibility = BankDepositXpEligibility.ELIGIBLE,
    val valuePerUnit: Long = 100L,
) {
    init {
        require(occurredAt.toEpochMilli() > 0)
        require(policy.source == ExperienceSource.BANK_DEPOSIT)
        require(boostMultiplier.isFinite() && boostMultiplier >= 1.0)
        require(valuePerUnit > 0)
    }

    /** Multiply the complete base award before rounding, matching ordinary XP boost semantics. */
    fun requestedXp(units: Int): Int? = try {
        val base = Math.multiplyExact(policy.awardXp, units)
        BigDecimal.valueOf(base.toLong()).multiply(BigDecimal.valueOf(boostMultiplier))
            .setScale(0, RoundingMode.FLOOR).intValueExact().takeIf { it > 0 }
    } catch (_: ArithmeticException) {
        null
    }
}

enum class BankDepositXpEligibility { ELIGIBLE, AFK, DISABLED, INVALID_UNITS }

/** WAITING is a response only; all other values can be retained in the delivery journal. */
enum class BankDepositXpStatus {
    PREPARED, READY, WAITING, AWARDED, NO_UNITS, AFK, DISABLED, INVALID_UNITS,
    CAPPED, STALE_RUN, CHAPTER_FROZEN, UNLIMITED, CANCELLED,
}

data class BankDepositXpCompletion(
    val transactionId: UUID,
    val guildId: UUID,
    val status: BankDepositXpStatus,
    val acceptedXp: Int = 0,
    val leveledUpTo: Int? = null,
    val awardedNow: Boolean = false,
    val cachePending: Boolean = false,
)
