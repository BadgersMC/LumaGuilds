package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.lumalyte.lg.application.persistence.DiplomacyHistory
import net.lumalyte.lg.domain.entities.DiplomacyEntry
import net.lumalyte.lg.domain.entities.DiplomacyKind
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals

/** REQ-203: one entry per guild, worded from that guild's side. */
class DiplomacyLogTest {
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()
    private val actor = UUID.randomUUID()
    private val now = Instant.parse("2026-11-10T10:00:00Z")
    private val history = mockk<DiplomacyHistory>(relaxed = true)
    private val saved = slot<List<DiplomacyEntry>>()
    private val log = DiplomacyLog(history, Clock.fixed(now, ZoneOffset.UTC))

    init {
        every { history.record(capture(saved)) } returns Unit
    }

    private fun kinds() = saved.captured.associate { it.guildId to it.kind }

    @Test
    fun `symmetric changes give both guilds the same kind`() {
        log.allied(a, b, actor)
        assertEquals(mapOf(a to DiplomacyKind.ALLIED, b to DiplomacyKind.ALLIED), kinds())
        assertEquals(setOf(b, a), saved.captured.map { it.otherGuildId }.toSet())
        assertEquals(listOf(actor, actor), saved.captured.map { it.actorId })
        assertEquals(listOf(now, now), saved.captured.map { it.at })
    }

    @Test
    fun `enemy declarations and war results depend on the side`() {
        log.declaredEnemies(a, b, actor)
        assertEquals(mapOf(a to DiplomacyKind.DECLARED_ENEMIES, b to DiplomacyKind.DECLARED_ENEMIES_BY), kinds())
        log.warEnded(a, b, winner = b)
        assertEquals(mapOf(a to DiplomacyKind.WAR_LOST, b to DiplomacyKind.WAR_WON), kinds())
        log.warEnded(a, b, winner = null)
        assertEquals(mapOf(a to DiplomacyKind.WAR_DRAWN, b to DiplomacyKind.WAR_DRAWN), kinds())
    }

    @Test
    fun `truces keep their length`() {
        log.truceStarted(a, b, actor, days = 3)
        assertEquals(listOf(3, 3), saved.captured.map { it.days })
        log.truceEnded(a, b)
        assertEquals(listOf(null, null), saved.captured.map { it.actorId })
    }

    @Test
    fun `a failed write never throws`() {
        every { history.record(any()) } throws IllegalStateException("db down")
        log.peace(a, b, actor)
        assertEquals(emptyList(), DiplomacyLog(mockk { every { page(any(), any(), any()) } throws IllegalStateException() }).page(a, 0, 10))
    }
}
