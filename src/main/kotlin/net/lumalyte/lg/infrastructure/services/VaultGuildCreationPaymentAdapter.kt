package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.ExternalTransferResult
import net.lumalyte.lg.application.services.GuildCreationPaymentPort
import net.lumalyte.lg.application.services.PersonalEconomyPort
import net.lumalyte.lg.application.services.PhysicalCommitResult
import net.lumalyte.lg.application.services.PhysicalGoldReservation
import net.lumalyte.lg.application.services.PhysicalReservationResult
import java.util.UUID

/**
 * Vault has no idempotency key. Every external withdrawal or refund is preceded
 * by a durable intent; an unacknowledged outcome is held for staff reconciliation
 * instead of retrying a transfer with a fresh transaction ID.
 */
class VaultGuildCreationPaymentAdapter(
    private val personal: PersonalEconomyPort,
    private val journal: GuildCreationVaultJournal,
) : GuildCreationPaymentPort {
    override fun pendingReservation(playerId: UUID): UUID? = journal.pending(playerId)

    override fun reserve(transactionId: UUID, playerId: UUID, requestedValue: Long): PhysicalReservationResult {
        if (requestedValue <= 0) return PhysicalReservationResult.Unavailable
        val available = try {
            if (!personal.isAvailable()) return PhysicalReservationResult.Unavailable
            personal.balance(playerId)
        } catch (_: Exception) {
            return PhysicalReservationResult.Unavailable
        } ?: return PhysicalReservationResult.Unavailable
        if (available < requestedValue) return PhysicalReservationResult.Insufficient

        val receipt = PhysicalGoldReservation(transactionId, playerId, requestedValue)
        return try {
            when (journal.claim(receipt)) {
                is GuildCreationVaultJournal.Claim.Pending -> return PhysicalReservationResult.Unknown
                GuildCreationVaultJournal.Claim.Claimed -> Unit
            }
            if (!journal.transition(transactionId, "CLAIMED", "DEBITING"))
                return PhysicalReservationResult.Unknown
            val debit = try {
                personal.debit(playerId, requestedValue)
            } catch (_: Exception) {
                // A thrown transfer may have changed the account.
                return PhysicalReservationResult.Unknown
            }
            when (debit) {
                ExternalTransferResult.Applied ->
                    if (journal.transition(transactionId, "DEBITING", "DEBITED"))
                        PhysicalReservationResult.Reserved(receipt) else PhysicalReservationResult.Unknown
                // Once debit was invoked, a negative provider response is not a
                // durable no-effect receipt. Keep DEBITING guarded for reconciliation.
                ExternalTransferResult.Unavailable,
                is ExternalTransferResult.Rejected,
                is ExternalTransferResult.Failed -> PhysicalReservationResult.Unknown
            }
        } catch (_: Exception) {
            PhysicalReservationResult.Unknown
        }
    }

    override fun commit(reservation: PhysicalGoldReservation): PhysicalCommitResult {
        return try {
            val state = journal.get(reservation.id)
            if (state?.payment != reservation) return PhysicalCommitResult.Unknown
            if (state.phase == "COMMITTED") return PhysicalCommitResult.Committed
            if (state.phase != "DEBITED") return PhysicalCommitResult.Unknown
            if (journal.finish(reservation, "DEBITED", "COMMITTED"))
                PhysicalCommitResult.Committed else PhysicalCommitResult.Unknown
        } catch (_: Exception) {
            PhysicalCommitResult.Unknown
        }
    }

    override fun restore(reservation: PhysicalGoldReservation): Boolean {
        return try {
            val state = journal.get(reservation.id)
            if (state?.payment != reservation) return false
            if (state.phase == "RESTORED") return true
            if (state.phase != "DEBITED") return false
            if (!journal.transition(reservation.id, "DEBITED", "CREDITING")) return false
            // CREDITING is an irreversible no-replay boundary even after process restart.
            val credited = try {
                personal.credit(reservation.playerId, reservation.value)
            } catch (_: Exception) {
                return false
            }
            credited == ExternalTransferResult.Applied &&
                journal.finish(reservation, "CREDITING", "RESTORED")
        } catch (_: Exception) {
            false
        }
    }
}
