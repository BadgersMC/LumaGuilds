package net.lumalyte.lg.application.persistence

import java.util.UUID

interface ClaimTransferRequestRepository {
    fun hasActive(claimId: UUID, playerId: UUID, nowEpochSeconds: Long): Boolean
    fun offer(claimId: UUID, playerId: UUID, expiresAtEpochSeconds: Long): Boolean
    fun withdraw(claimId: UUID, playerId: UUID): Boolean
    fun clearClaim(claimId: UUID): Boolean
}
