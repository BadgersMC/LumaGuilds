package net.lumalyte.lg.domain.rewards

data class RewardOwnershipSnapshot(val version: Long, val ownership: RewardOwnership) {
    init { require(version >= 0) }
}

sealed interface RewardOwnershipRead {
    data class Found(val snapshot: RewardOwnershipSnapshot) : RewardOwnershipRead
    data object Missing : RewardOwnershipRead
    data class Failed(val reason: String) : RewardOwnershipRead
}

sealed interface RewardOwnershipWrite {
    data class Saved(val snapshot: RewardOwnershipSnapshot) : RewardOwnershipWrite
    data object Conflict : RewardOwnershipWrite
    data class Failed(val reason: String) : RewardOwnershipWrite
}
