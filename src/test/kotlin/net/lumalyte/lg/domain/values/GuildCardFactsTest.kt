package net.lumalyte.lg.domain.values

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** REQ-195 and REQ-196. */
class GuildCardFactsTest {
    @Test
    fun `guild card lists home, banner, vault and shop in order`() {
        assertEquals(
            listOf(GuildCardFact.HOME to true, GuildCardFact.BANNER to false, GuildCardFact.VAULT to true, GuildCardFact.SHOP to false),
            GuildCardFacts(home = true, banner = false, vault = true, shop = false).lines(),
        )
    }

    @Test
    fun `shop line is left out when no lookup is registered`() {
        assertEquals(
            listOf(GuildCardFact.HOME to false, GuildCardFact.BANNER to false, GuildCardFact.VAULT to false),
            GuildCardFacts(home = false, banner = false, vault = false, shop = null).lines(),
        )
    }

    @Test
    fun `a relations row shows 8 guilds and counts the rest`() {
        assertEquals(RelationRow(listOf(1, 2), 0), RelationRow.of(listOf(1, 2)))
        val row = RelationRow.of((1..11).toList())
        assertEquals((1..8).toList(), row.shown)
        assertEquals(3, row.more)
    }
}
