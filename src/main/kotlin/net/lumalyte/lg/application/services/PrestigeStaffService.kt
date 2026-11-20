package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.PrestigeAdministration
import net.lumalyte.lg.application.persistence.PrestigeCreditLedger
import net.lumalyte.lg.application.persistence.PrestigeHistory
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.persistence.RewardOwnershipRepository
import net.lumalyte.lg.domain.rewards.PrestigeAdjustment
import net.lumalyte.lg.domain.rewards.PrestigeAdjustmentResult
import net.lumalyte.lg.domain.rewards.PrestigeRecord
import net.lumalyte.lg.domain.rewards.PrestigeResult
import net.lumalyte.lg.domain.rewards.RewardDefinition
import net.lumalyte.lg.domain.rewards.RewardOwnershipRead
import java.time.Instant
import java.util.UUID

/** REQ-184: what staff see for one guild. */
data class PrestigeStaffInfo(
    val prestigeCount: Int,
    val maxCount: Int,
    val level: Int?,
    val keptRewards: Set<String>,
    val history: List<PrestigeRecord>,
    val adjustments: List<PrestigeAdjustment>,
)

/** REQ-184 to REQ-187: staff prestige tools. Every change is logged by the caller. */
class PrestigeStaffService(
    private val owners: RewardOwnershipRepository,
    private val progression: ProgressionRepository,
    private val history: PrestigeHistory,
    private val admin: PrestigeAdministration,
    private val ledger: PrestigeCreditLedger,
    private val prestige: GuildPrestigeService,
    private val standings: GuildPrestigeStandings?,
    private val config: ConfigService,
    private val creditOnlineMembers: (UUID) -> Unit,
    private val clock: () -> Instant = Instant::now,
) {
    fun info(guildId: UUID): PrestigeStaffInfo? {
        val snapshot = (owners.read(guildId) as? RewardOwnershipRead.Found)?.snapshot ?: return null
        return PrestigeStaffInfo(
            prestigeCount = snapshot.ownership.prestigeCount,
            maxCount = config.loadConfig().progression.prestige.maxCount,
            level = runCatching { progression.getGuildProgression(guildId)?.currentLevel }.getOrNull(),
            keptRewards = snapshot.ownership.permanent,
            history = runCatching { history.history(guildId) }.getOrDefault(emptyList()),
            adjustments = runCatching { admin.adjustments(guildId) }.getOrDefault(emptyList()),
        )
    }

    fun setCount(actorId: UUID, guildId: UUID, count: Int): PrestigeAdjustmentResult {
        val result = admin.setPrestigeCount(guildId, count, actorId, clock())
        if (result is PrestigeAdjustmentResult.Applied) standings?.forget(guildId)
        return result
    }

    /** REQ-185: the kept rewards a lowering to [count] would remove. */
    fun rewardsDroppedBy(guildId: UUID, count: Int): List<String> = admin.rewardsDroppedBy(guildId, count)

    fun grantChoices(guildId: UUID): List<RewardDefinition> = prestige.grantChoices(guildId)

    fun grant(actorId: UUID, guildId: UUID, rewardId: String): PrestigeResult =
        prestige.staffGrant(actorId, guildId, rewardId)

    /** The prestiges a rerun would cover: all applied ones, or only [tier]. */
    fun rerunTargets(guildId: UUID, tier: Int?): List<PrestigeRecord> =
        history.history(guildId).filter { tier == null || it.tier == tier }

    /** REQ-187: returns the prestiges made creditable again (online members are credited now). */
    fun rerun(guildId: UUID, tier: Int?): List<PrestigeRecord> {
        val targets = rerunTargets(guildId, tier)
        if (targets.isEmpty()) return targets
        ledger.forget(targets.map { it.transactionId }.toSet())
        creditOnlineMembers(guildId)
        return targets
    }
}
