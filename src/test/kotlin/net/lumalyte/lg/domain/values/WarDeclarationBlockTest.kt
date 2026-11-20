package net.lumalyte.lg.domain.values

import net.lumalyte.lg.infrastructure.services.WarServiceBukkit
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** REQ-239 */
class WarDeclarationBlockTest {
    private val now = Instant.parse("2026-11-14T12:00:00Z")

    private fun block(
        declaration: Instant? = null,
        own: Instant? = null,
        target: Instant? = null,
        ownFull: Boolean = false,
        targetFull: Boolean = false,
    ) = WarServiceBukkit.declarationBlock(now, declaration, own, target, ownFull, targetFull)

    @Test
    fun `declaring again after a surrendered war reports the declaration cooldown and its end`() {
        val end = now.plus(Duration.ofHours(20))
        assertEquals(WarDeclarationBlock(WarDeclarationBlock.Reason.DECLARATION_COOLDOWN, end), block(declaration = end))
    }

    @Test
    fun `expired cooldowns do not block`() {
        assertNull(block(declaration = now.minusSeconds(1), own = now, target = now.minusSeconds(60)))
    }

    @Test
    fun `cooldowns are reported before war limits, own guild before target`() {
        val soon = now.plusSeconds(600)
        assertEquals(WarDeclarationBlock.Reason.OWN_FARMING_COOLDOWN, block(own = soon, target = soon, ownFull = true)?.reason)
        assertEquals(WarDeclarationBlock.Reason.TARGET_FARMING_COOLDOWN, block(target = soon, ownFull = true)?.reason)
        assertEquals(WarDeclarationBlock.Reason.OWN_WAR_LIMIT, block(ownFull = true, targetFull = true)?.reason)
        assertEquals(WarDeclarationBlock.Reason.TARGET_WAR_LIMIT, block(targetFull = true)?.reason)
    }

    @Test
    fun `remaining time rounds up to the minute and formats compactly`() {
        val b = WarDeclarationBlock(WarDeclarationBlock.Reason.DECLARATION_COOLDOWN, now.plusSeconds(3 * 3600 + 20 * 60 + 5))
        assertEquals("3h 21m", WarDeclarationBlock.format(b.remaining(now)!!))
        assertEquals("1d 4h", WarDeclarationBlock.format(Duration.ofHours(28)))
        assertEquals("1m", WarDeclarationBlock.format(Duration.ofSeconds(10)))
        assertEquals(Duration.ZERO, b.remaining(now.plus(Duration.ofDays(1))))
    }
}
