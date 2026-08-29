package net.lumalyte.lg.domain.entities

import net.lumalyte.lg.domain.values.ExperienceSource
import java.time.Instant
import java.util.UUID

data class ExperienceAwardRequest(
    val guildId: UUID,
    val actorId: UUID?,
    val source: ExperienceSource,
    val units: Int,
    val occurredAt: Instant,
    val eligible: Boolean = true,
    val transactionId: UUID = UUID.randomUUID(),
)

enum class AwardRejection {
    SOURCE_DISABLED,
    POLICY_MISMATCH,
    INELIGIBLE,
    INVALID_UNITS,
    SUSPICIOUS_OR_AFK,
}

sealed interface ExperienceAwardResult {
    data object Duplicate : ExperienceAwardResult

    data class Awarded(
        val acceptedXp: Int,
        val totalUsedXp: Int,
        val capped: Boolean,
        val leveledUpTo: Int? = null,
    ) : ExperienceAwardResult

    data class Rejected(val reason: AwardRejection) : ExperienceAwardResult

    data class NoAllowance(
        val capXp: Int,
        val totalUsedXp: Int,
    ) : ExperienceAwardResult
}
