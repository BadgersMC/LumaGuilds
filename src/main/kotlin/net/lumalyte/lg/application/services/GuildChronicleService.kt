package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.ChronicleGoal
import net.lumalyte.lg.application.persistence.ChronicleGoalRepository
import net.lumalyte.lg.domain.rewards.*
import java.util.UUID

enum class ChronicleBranch {
    ALL, TREASURY, TRAVEL, OUTPOSTS;

    fun includes(effect: RewardEffect): Boolean = when (this) {
        ALL -> true
        TREASURY -> effect is RewardEffect.BankCapacity || effect is RewardEffect.WithdrawalFee
        TRAVEL -> effect is RewardEffect.HomeCooldown || effect == RewardEffect.AllyHomes
        OUTPOSTS -> effect is RewardEffect.HomeCapacity
    }
}

data class ChronicleLevel(val level: Int, val automaticBankCapacity: Long, val offers: List<RewardOffer>)
data class GuildChronicle(
    val state: GuildRewardRead.Available,
    val goal: ChronicleGoal,
    val canTrack: Boolean,
    val page: Int,
    val levels: List<ChronicleLevel>,
)
enum class ChronicleGoalWrite { SAVED, CONFLICT, DENIED, INVALID, UNAVAILABLE }

/** Read-only catalog projection plus a separate, non-financial guild preference. */
class GuildChronicleService(
    private val catalog: RewardCatalog,
    private val goals: ChronicleGoalRepository,
    private val isMember: (UUID, UUID) -> Boolean,
    private val canManage: (UUID, UUID) -> Boolean,
    private val rewards: (UUID) -> GuildRewardRead,
) {
    fun read(actor: UUID, guild: UUID, page: Int, branch: ChronicleBranch): GuildChronicle? = try {
        if (!isMember(actor, guild)) null else {
            val state = rewards(guild) as? GuildRewardRead.Available
            if (state == null) null else {
                val boundedPage = page.coerceIn(0, 9)
                val first = boundedPage * 10 + 1
                val offers = state.entitlements.offers.associateBy { it.reward.id }
                val levels = (first..first + 9).map { level ->
                    val tier = catalog.level(level)
                    ChronicleLevel(level, tier.automaticBankCapacity, tier.rewards
                        .filter { branch.includes(it.effect) }.map { offers.getValue(it.id) })
                }
                val goal = goals.read(guild)
                // Unknown persisted IDs are a data/configuration error, never silently cleared.
                require(goal.rewardId == null || catalog.find(goal.rewardId) != null)
                GuildChronicle(state, goal, canManage(actor, guild), boundedPage, levels)
            }
        }
    } catch (_: Exception) { null }

    fun track(actor: UUID, guild: UUID, expected: ChronicleGoal, rewardId: String?): ChronicleGoalWrite = try {
        when {
            !isMember(actor, guild) || !canManage(actor, guild) -> ChronicleGoalWrite.DENIED
            rewardId != null && catalog.find(rewardId) == null -> ChronicleGoalWrite.INVALID
            rewards(guild) !is GuildRewardRead.Available -> ChronicleGoalWrite.UNAVAILABLE
            goals.compareAndSet(guild, expected, rewardId) -> ChronicleGoalWrite.SAVED
            else -> ChronicleGoalWrite.CONFLICT
        }
    } catch (_: Exception) { ChronicleGoalWrite.UNAVAILABLE }
}
