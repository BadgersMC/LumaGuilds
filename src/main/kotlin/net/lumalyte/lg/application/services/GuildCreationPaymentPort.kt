package net.lumalyte.lg.application.services

import java.util.UUID

/**
 * Paid founding's reservation/commit boundary. Physical inventory and personal Vault
 * implementations must both durably hold unknown outcomes before allowing another charge.
 * Reservation DTOs are shared with the existing physical payment flow.
 */
interface GuildCreationPaymentPort {
    fun pendingReservation(playerId: UUID): UUID?
    fun reserve(transactionId: UUID, playerId: UUID, requestedValue: Long): PhysicalReservationResult
    fun commit(reservation: PhysicalGoldReservation): PhysicalCommitResult
    fun restore(reservation: PhysicalGoldReservation): Boolean
}
