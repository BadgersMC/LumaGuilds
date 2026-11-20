package net.lumalyte.lg.domain.rewards

import java.time.Instant
import java.util.UUID

data class PrestigeQuote(
    val transactionId: UUID,
    val guildId: UUID,
    val actorId: UUID,
    val retainedRewardId: String,
    val quotedFee: Long,
    val expectedOwnershipVersion: Long,
    val expectedPrestigeCount: Int,
)

enum class PrestigeRejection {
    UNAVAILABLE,
    UNAUTHORIZED,
    UNINITIALIZED,
    NOT_LEVEL_100,
    MAX_PRESTIGE,
    INVALID_SELECTION,
    STALE_STATE,
    FEE_CHANGED,
    ACTIVE_WAR,
    INSUFFICIENT_FUNDS,
    POST_PRESTIGE_CAPACITY,
    PENDING_GOLD,
    FROZEN,
    ID_CONFLICT,
}

sealed interface PrestigeResult {
    data class Applied(
        val transactionId: UUID,
        val retainedRewardId: String,
        val fee: Long,
        val oldBalance: Long,
        val newBalance: Long,
        val ownershipVersion: Long,
        val prestigeCount: Int,
        val postBankCapacity: Long,
        val homeCapacity: Int,
        /** REQ-175: XP carried into the next climb, and the level it starts at. */
        val carriedExperience: Int = 0,
        val postLevel: Int = 1,
    ) : PrestigeResult

    data class Rejected(val reason: PrestigeRejection) : PrestigeResult
    data class Failed(val transactionId: UUID) : PrestigeResult
}

data class PrestigeOverview(
    val enabled: Boolean,
    val currentLevel: Int,
    val prestigeCount: Int,
    val maxPrestigeCount: Int,
    val nextFee: Long?,
    val choices: List<RewardDefinition>,
)
/** One applied prestige, read from its receipt and its canonical gold-journal debit. */
/** REQ-185: a staff correction of a guild's prestige count. */
data class PrestigeAdjustment(
    val guildId: UUID,
    val actorId: UUID,
    val oldCount: Int,
    val newCount: Int,
    val at: Instant,
)

sealed interface PrestigeAdjustmentResult {
    /** [removedRewards]: the kept rewards dropped with the lowered tiers, newest first. */
    data class Applied(val oldCount: Int, val newCount: Int, val removedRewards: List<String> = emptyList()) :
        PrestigeAdjustmentResult
    data object Unchanged : PrestigeAdjustmentResult
    /** Raising the count needs a kept reward per tier, so it goes through a staff grant. */
    data object RaiseNeedsGrant : PrestigeAdjustmentResult
    data object OutOfRange : PrestigeAdjustmentResult
    data object NotFound : PrestigeAdjustmentResult
    data object Failed : PrestigeAdjustmentResult
}

data class PrestigeRecord(
    val transactionId: UUID,
    val guildId: UUID,
    val actorId: UUID,
    val tier: Int,
    val retainedRewardId: String,
    val appliedAt: Instant,
)

/** REQ-301: an applied prestige and the guild-bank gold it cost. */
data class AppliedPrestige(val record: PrestigeRecord, val feePaid: Long)

/** A newly committed prestige as published to listeners (REQ-153). */
data class PrestigeCompletion(
    val transactionId: UUID,
    val guildId: UUID,
    val actorId: UUID,
    val tier: Int,
    val retainedRewardId: String,
    val appliedAt: Instant,
    val firstToReachTier: Boolean,
)

/** A guild's prestige as shown in placeholders (REQ-155). */
data class PrestigeStanding(
    /** Current tier, 0..6. */
    val tier: Int,
    val lastAppliedAt: Instant?,
    /** Highest tier this guild was first on the server to reach, 0 if none. */
    val firstTier: Int,
)

/** A prestiged guild as it stood when it was disbanded (REQ-163). */
data class DisbandedPrestigeGuild(
    val guildId: UUID,
    val name: String,
    val prestigeCount: Int,
    val disbandedAt: Instant,
)
