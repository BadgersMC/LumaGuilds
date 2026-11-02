package net.lumalyte.lg.interaction.menus

import net.lumalyte.lg.domain.entities.TransactionType
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.fail

/** Covers the player-facing actor and amount projections of bank history. */
internal class BankHistoryPresentationTest {
    private val systemLabel = "System"
    private val unknownLabel = "Unknown"

    /** The reserved system actor must not trigger a player lookup. */
    @Test
    fun systemSkipsPlayerLookup() {
        val name =
            BankHistoryPresentation.actorName(UUID(0, 0), systemLabel, unknownLabel) {
                fail("system is not a player")
            }
        assertEquals(systemLabel, name)
    }

    /** Known names and unresolved player identities remain distinct. */
    @Test
    fun playerNamesRemainDistinct() {
        val player = UUID.randomUUID()
        val known = BankHistoryPresentation.actorName(player, systemLabel, unknownLabel) { "FainNoir" }
        val unknown = BankHistoryPresentation.actorName(player, systemLabel, unknownLabel) { null }
        assertEquals("FainNoir", known)
        assertEquals(unknownLabel, unknown)
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
