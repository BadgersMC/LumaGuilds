package net.lumalyte.lg.interaction.menus

import net.lumalyte.lg.domain.entities.TransactionType
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.fail

/** Covers the player-facing actor and amount projections of bank history. */
internal class BankHistoryPresentationTest {
    private val SYSTEM_LABEL = "System"
    private val UNKNOWN_LABEL = "Unknown"

    /** The reserved system actor must not trigger a player lookup. */
    @Test
    fun systemSkipsPlayerLookup() {
        val name = BankHistoryPresentation.actorName(UUID(0, 0), SYSTEM_LABEL, UNKNOWN_LABEL) {
            fail("system is not a player")
        }
        assertEquals(SYSTEM_LABEL, name)
    }

    /** Known names and unresolved player identities remain distinct. */
    @Test
    fun playerNamesRemainDistinct() {
        val player = UUID.randomUUID()
        val known = BankHistoryPresentation.actorName(player, SYSTEM_LABEL, UNKNOWN_LABEL) { "FainNoir" }
        val unknown = BankHistoryPresentation.actorName(player, SYSTEM_LABEL, UNKNOWN_LABEL) { null }
        assertEquals("FainNoir", known)
        assertEquals(UNKNOWN_LABEL, unknown)
    }

    /** Only deposits appear as credits; every other transaction type is a debit. */
    @Test
    fun onlyDepositsAreCredits() {
        assertEquals("+2", BankHistoryPresentation.signedAmount(TransactionType.DEPOSIT, 2))
        for (type in listOf(TransactionType.WITHDRAWAL, TransactionType.FEE, TransactionType.DEDUCTION)) {
            assertEquals("-2", BankHistoryPresentation.signedAmount(type, 2))
        }
    }
}
