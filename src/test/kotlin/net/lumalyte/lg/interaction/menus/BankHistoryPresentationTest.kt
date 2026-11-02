package net.lumalyte.lg.interaction.menus

import net.lumalyte.lg.domain.entities.TransactionType
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.fail

class BankHistoryPresentationTest {
    @Test
    fun `system actor does not invoke player lookup`() {
        assertEquals("System", BankHistoryPresentation.actorName(UUID(0, 0), "System", "Unknown") { fail("system is not a player") })
    }

    @Test
    fun `known and unknown player identities stay distinct`() {
        val player = UUID.randomUUID()
        assertEquals("FainNoir", BankHistoryPresentation.actorName(player, "System", "Unknown") { "FainNoir" })
        assertEquals("Unknown", BankHistoryPresentation.actorName(player, "System", "Unknown") { null })
    }

    @Test
    fun `only deposits are credits`() {
        assertEquals("+2", BankHistoryPresentation.signedAmount(TransactionType.DEPOSIT, 2))
        for (type in listOf(TransactionType.WITHDRAWAL, TransactionType.FEE, TransactionType.DEDUCTION)) {
            assertEquals("-2", BankHistoryPresentation.signedAmount(type, 2))
        }
    }
}
