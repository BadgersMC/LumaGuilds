package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.PrestigeHistory
import net.lumalyte.lg.application.persistence.RewardOwnershipRepository
import net.lumalyte.lg.domain.rewards.PrestigeCompletion
import net.lumalyte.lg.domain.rewards.PrestigeStanding
import net.lumalyte.lg.domain.rewards.RewardOwnershipRead
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Cached prestige standings for placeholders (REQ-155). TAB and nametags ask often,
 * so each guild is read at most once per [ttl], and the six server-first holders
 * once per [ttl] for all guilds. A new prestige drops both caches immediately.
 */
class GuildPrestigeStandings(
    private val owners: RewardOwnershipRepository,
    private val history: PrestigeHistory,
    private val ttl: Duration = Duration.ofSeconds(30),
    private val clock: () -> Instant = Instant::now,
) : GuildPrestigeNotifier {
    private data class Cached<T>(val expiresAt: Instant, val value: T)

    private val standings = ConcurrentHashMap<UUID, Cached<PrestigeStanding?>>()
    @Volatile private var firstHolders: Cached<Map<Int, UUID>>? = null

    fun standing(guildId: UUID): PrestigeStanding? {
        val now = clock()
        standings[guildId]?.takeIf { it.expiresAt.isAfter(now) }?.let { return it.value }
        val value = read(guildId, now)
        standings[guildId] = Cached(now.plus(ttl), value)
        return value
    }

    override fun prestiged(completion: PrestigeCompletion) {
        forget(completion.guildId)
    }

    /** Drops cached standings after a change outside a prestige (REQ-185 staff correction). */
    fun forget(guildId: UUID) {
        standings.remove(guildId)
        firstHolders = null
    }

    private fun read(guildId: UUID, now: Instant): PrestigeStanding? = runCatching {
        val tier = when (val read = owners.read(guildId)) {
            is RewardOwnershipRead.Found -> read.snapshot.ownership.prestigeCount
            RewardOwnershipRead.Missing -> 0
            is RewardOwnershipRead.Failed -> return null
        }
        val last = history.history(guildId).lastOrNull()?.appliedAt
        val firstTier = firsts(now).filterValues { it == guildId }.keys.maxOrNull() ?: 0
        PrestigeStanding(tier, last, firstTier)
    }.getOrNull()

    private fun firsts(now: Instant): Map<Int, UUID> {
        firstHolders?.takeIf { it.expiresAt.isAfter(now) }?.let { return it.value }
        val holders = (1..MAX_TIER).mapNotNull { tier -> history.firstToReach(tier)?.let { tier to it.guildId } }.toMap()
        firstHolders = Cached(now.plus(ttl), holders)
        return holders
    }

    private companion object {
        const val MAX_TIER = 6
    }
}
