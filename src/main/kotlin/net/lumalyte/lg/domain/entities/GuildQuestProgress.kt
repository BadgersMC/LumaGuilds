package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

data class GuildQuestProgress(
    val weekId: String,
    val questId: String,
    val guildId: UUID,
    val currentCount: Long = 0,
    val claimed: Boolean = false,
    val completedAt: Instant? = null
) {
    fun withIncrementedCount(amount: Long, targetCount: Long, now: Instant = Instant.now()): GuildQuestProgress {
        require(amount >= 0) { "Progress increment cannot be negative" }
        val updated = currentCount + amount
        return copy(
            currentCount = updated,
            completedAt = completedAt ?: if (targetCount > 0 && updated >= targetCount) now else null
        )
    }

    fun withClaimed(): GuildQuestProgress = copy(claimed = true)

    fun isCompletable(targetCount: Long): Boolean = targetCount > 0 && currentCount >= targetCount && !claimed
}

data class WeeklyQuestSet(
    val weekId: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val quests: List<QuestDefinition>
) {
    init {
        require(endsAt.isAfter(startsAt)) { "Weekly quest end must be after start" }
        require(quests.map { it.id }.distinct().size == quests.size) { "Weekly quest ids must be unique" }
    }
}
