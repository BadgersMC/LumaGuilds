package net.lumalyte.lg.application.persistence

import java.util.UUID

/**
 * Players each player blocked from inviting them (and from guild, ally and party chat). Reads come from memory, so
 * they are safe on the async chat thread; [isAvailable] is false when the stored blocks could not be loaded, and
 * callers then refuse invites rather than let a block be skipped.
 */
interface InviteBlockRepository {
    val isAvailable: Boolean
    fun isBlocked(ownerId: UUID, targetId: UUID): Boolean
    fun blockedBy(ownerId: UUID): Set<UUID>
    /** Saves the change durably before it takes effect; false when it could not be saved. Idempotent. */
    fun setBlocked(ownerId: UUID, targetId: UUID, blocked: Boolean): Boolean
}
