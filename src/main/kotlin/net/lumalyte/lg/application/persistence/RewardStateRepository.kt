package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.rewards.RewardStateRead
import java.util.UUID

fun interface RewardStateRepository {
    fun read(guildId: UUID): RewardStateRead
}
