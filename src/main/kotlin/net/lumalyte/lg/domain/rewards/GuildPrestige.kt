package net.lumalyte.lg.domain.rewards

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