package net.lumalyte.lg.domain.values

import net.lumalyte.lg.domain.values.AdvancementXpEligibility.Decision
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class AdvancementXpEligibilityTest {
    private val main = UUID.randomUUID()
    private val alt = UUID.randomUUID()
    private val friend = UUID.randomUUID()

    private fun decide(addresses: Map<UUID, String?>, guildOnline: Set<UUID>, serverWide: Map<String, Int> = emptyMap(), gateway: Int = 4) =
        AdvancementXpEligibility.decide(main, guildOnline, { addresses[it] },
            { ip -> serverWide[ip] ?: addresses.values.count { it == ip } }, gateway)

    @Test fun `a member on their own connection is paid`() {
        assertEquals(Decision.ELIGIBLE, decide(mapOf(main to "1.1.1.1", friend to "2.2.2.2"), setOf(main, friend)))
    }

    @Test fun `another online guild member on the same connection holds it back`() {
        assertEquals(Decision.SHARED, decide(mapOf(main to "1.1.1.1", alt to "1.1.1.1"), setOf(main, alt)))
    }

    @Test fun `someone on the same connection outside the guild doesn't count`() {
        assertEquals(Decision.ELIGIBLE, decide(mapOf(main to "1.1.1.1", alt to "1.1.1.1"), setOf(main)))
    }

    @Test fun `a connection shared by many players is a gateway, not an alt`() {
        val addresses = mapOf(main to "10.0.0.5", alt to "10.0.0.5")
        assertEquals(Decision.ELIGIBLE, decide(addresses, setOf(main, alt), serverWide = mapOf("10.0.0.5" to 4)))
        assertEquals(Decision.SHARED, decide(addresses, setOf(main, alt), serverWide = mapOf("10.0.0.5" to 3)))
        assertEquals(Decision.SHARED, decide(addresses, setOf(main, alt), serverWide = mapOf("10.0.0.5" to 40), gateway = 0))
    }

    @Test fun `an unreadable connection is reported, not treated as shared`() {
        assertEquals(Decision.UNKNOWN, decide(mapOf(main to null, alt to null), setOf(main, alt)))
    }
}
