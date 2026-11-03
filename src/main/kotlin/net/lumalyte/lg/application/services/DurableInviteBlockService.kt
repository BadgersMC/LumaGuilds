package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.InviteBlockWriteResult
import java.util.UUID

/** Synchronous durable operations sharing the legacy service's storage, cache, and irreversible lifecycle gate. */
internal interface DurableInviteBlockService : AutoCloseable {
    fun isBlockedDurable(ownerId: UUID, targetId: UUID): Boolean

    fun setBlockedDurable(ownerId: UUID, targetId: UUID, blocked: Boolean): InviteBlockWriteResult

    /** Drains admitted synchronous operations and permanently rejects captured references. */
    override fun close()
}
