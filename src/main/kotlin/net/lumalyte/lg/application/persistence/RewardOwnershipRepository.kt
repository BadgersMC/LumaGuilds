package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.rewards.RewardOwnership
import net.lumalyte.lg.domain.rewards.RewardOwnershipRead
import net.lumalyte.lg.domain.rewards.RewardOwnershipWrite
import java.util.UUID

/** Storage foundation; no method is a paid-purchase or prestige use case. */
interface RewardOwnershipRepository {
    fun read(guildId: UUID): RewardOwnershipRead
    /** Explicit baseline from new-guild creation or verified migration; never an upsert. */
    fun initialize(guildId: UUID, initialHomeCapacity: Int): RewardOwnershipWrite
    /** Optimistic concurrency; callers must not save after a separate gold debit. */
    fun save(guildId: UUID, expectedVersion: Long, ownership: RewardOwnership): RewardOwnershipWrite
}
