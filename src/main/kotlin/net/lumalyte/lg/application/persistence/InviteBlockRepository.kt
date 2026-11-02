package net.lumalyte.lg.application.persistence

import java.util.UUID

/**
 * Players each player blocked from inviting them (and from guild, ally and party chat). Reads come from memory, so
 * they are safe on the async chat thread; [isAvailable] is false when the stored blocks could not be loaded, and
 * callers then refuse invites rather than let a block be skipped.
 */
internal interface InviteBlockRepository {
    val isAvailable: Boolean

    /** Reads a personal block from the current cache. */
    fun isBlocked(ownerId: UUID, targetId: UUID): Boolean

    /** Returns a detached cache snapshot for the owner. */
    fun blockedBy(ownerId: UUID): Set<UUID>

    /** Confirms the saved state and cache; false includes an UNKNOWN database outcome, not only rollback. */
    fun setBlocked(ownerId: UUID, targetId: UUID, blocked: Boolean): Boolean

    /** Reads committed SQL state and reconciles the pair cache, or throws without claiming absence. */
    fun isBlockedDurable(ownerId: UUID, targetId: UUID): Boolean

    /** Always consults SQL, including already matching cache states; preserves commit certainty separately. */
    fun setBlockedDurable(ownerId: UUID, targetId: UUID, blocked: Boolean): InviteBlockWriteResult
}
