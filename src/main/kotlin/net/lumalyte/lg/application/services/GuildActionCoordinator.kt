package net.lumalyte.lg.application.services

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * Process-local serialization for guild-scoped state transitions that span repositories.
 *
 * Locks are always acquired in UUID order so two-guild operations (wars) cannot deadlock.
 * This closes the prestige-vs-war activation race while the database transaction provides
 * the durable atomicity for the prestige write itself.
 */
class GuildActionCoordinator {
    @PublishedApi
    internal val locks = ConcurrentHashMap<UUID, ReentrantLock>()

    inline fun <T> withGuilds(vararg guildIds: UUID, action: () -> T): T {
        val acquired = guildIds.distinct().sortedBy(UUID::toString).map { id ->
            locks.computeIfAbsent(id) { ReentrantLock() }
        }
        acquired.forEach(ReentrantLock::lock)
        return try {
            action()
        } finally {
            acquired.asReversed().forEach(ReentrantLock::unlock)
        }
    }
}