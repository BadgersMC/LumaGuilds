package net.lumalyte.lg.application.persistence

import java.util.UUID

interface ClaimTransferRequestRepository {
    fun hasActive(claimId: UUID, playerId: UUID, nowEpochSeconds: Long): Boolean
    fun offer(claimId: UUID, playerId: UUID, expiresAtEpochSeconds: Long): Boolean
    fun withdraw(claimId: UUID, playerId: UUID): Boolean

    /**
     * Atomically consumes a receiver's offer and every competing offer for the same claim.
     * Returns false if that receiver no longer has an unexpired offer at the instant of consumption.
     */
    fun consumeClaim(claimId: UUID, playerId: UUID, nowEpochSeconds: Long): Boolean

    fun clearClaim(claimId: UUID): Boolean
}
