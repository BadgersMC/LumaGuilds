package net.lumalyte.lg.infrastructure.persistence.guilds

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Memory-only projections; only committed SQL reads or writes may publish a pair. */
internal class InviteBlockCache {
    private val blocks = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    fun isBlocked(ownerId: UUID, targetId: UUID): Boolean = blocks[ownerId]?.contains(targetId) == true

    fun blockedBy(ownerId: UUID): Set<UUID> = blocks[ownerId]?.toSet().orEmpty()

    fun publish(ownerId: UUID, targetId: UUID, blocked: Boolean) {
        if (blocked) blocks.computeIfAbsent(ownerId) { ConcurrentHashMap.newKeySet() }.add(targetId)
        else blocks[ownerId]?.remove(targetId)
    }

    fun clear() = blocks.clear()
}
