package net.lumalyte.lg.interaction.menus

import net.lumalyte.lg.domain.entities.TransactionType
import java.util.UUID

/** Display-only interpretation of the persisted system actor and transaction direction. */
internal object BankHistoryPresentation {
    private val SYSTEM_ACTOR = UUID(0L, 0L)

    fun actorName(id: UUID, system: String, unknown: String, lookup: (UUID) -> String?): String =
        if (id == SYSTEM_ACTOR) system else lookup(id) ?: unknown

    fun signedAmount(type: TransactionType, amount: Int): String =
        (if (type == TransactionType.DEPOSIT) "+" else "-") + amount
}
