package net.lumalyte.lg.application.services

import java.util.UUID

data class PhysicalGoldRequest(
    val transactionId: UUID,
    val guildId: UUID,
    val playerId: UUID,
    val amount: Long,
    val description: String
)

data class PhysicalGoldReservation(val id: UUID, val playerId: UUID, val value: Long)

enum class PhysicalCommitResult { Committed, NotConsumed, Unknown }

sealed interface PhysicalReservationResult {
    data class Reserved(val reservation: PhysicalGoldReservation) : PhysicalReservationResult
    data object Insufficient : PhysicalReservationResult
    data object Unavailable : PhysicalReservationResult
    data object Unknown : PhysicalReservationResult
}

interface PhysicalGoldPort {
    /** Read-only value of currency eligible for reservation; null means unavailable. */
    fun availableValue(playerId: UUID): Long? = null

    fun reserve(transactionId: UUID, playerId: UUID, requestedValue: Long): PhysicalReservationResult
    fun reservation(transactionId: UUID): PhysicalGoldReservation? = null
    fun commit(reservation: PhysicalGoldReservation): PhysicalCommitResult
    fun restore(reservation: PhysicalGoldReservation): Boolean
    fun deliver(playerId: UUID, value: Long, transactionId: UUID): ExternalTransferResult

    data object Unavailable : PhysicalGoldPort {
        override fun reserve(transactionId: UUID, playerId: UUID, requestedValue: Long) = PhysicalReservationResult.Unavailable
        override fun commit(reservation: PhysicalGoldReservation) = PhysicalCommitResult.Unknown
        override fun restore(reservation: PhysicalGoldReservation) = false
        override fun deliver(playerId: UUID, value: Long, transactionId: UUID) = ExternalTransferResult.Unavailable
    }
}
