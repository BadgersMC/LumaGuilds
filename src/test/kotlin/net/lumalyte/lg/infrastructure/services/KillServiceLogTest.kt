package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildKillLog
import net.lumalyte.lg.domain.entities.Kill
import net.lumalyte.lg.domain.values.CombatSummary
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** REQ-199/REQ-200: the kill service records through the rules and reads from the log. */
class KillServiceLogTest {
    private val log = mockk<GuildKillLog>(relaxed = true)
    private val killer = UUID.randomUUID()
    private val victim = UUID.randomUUID()
    private val guildA = UUID.randomUUID()
    private val guildB = UUID.randomUUID()
    private val now = Instant.parse("2026-11-10T10:00:00Z")
    private val guilds = mutableMapOf(killer to guildA, victim to guildB)
    private val service = KillServiceLog(log, { guilds[it] }, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `records a kill with both guilds and the time`() {
        every { log.lastKill(killer, victim) } returns null
        val saved = slot<Kill>()
        every { log.record(capture(saved)) } returns Unit
        assertNotNull(service.recordKill(killer, victim, "IRON_SWORD"))
        assertEquals(guildA, saved.captured.killerGuildId)
        assertEquals(guildB, saved.captured.victimGuildId)
        assertEquals(now, saved.captured.timestamp)
        assertEquals("IRON_SWORD", saved.captured.weapon)
    }

    @Test
    fun `skipped kills are not saved`() {
        every { log.lastKill(killer, victim) } returns now.minusSeconds(60)
        assertNull(service.recordKill(killer, victim))
        guilds[victim] = guildA
        every { log.lastKill(killer, victim) } returns null
        assertNull(service.recordKill(killer, victim))
        verify(exactly = 0) { log.record(any()) }
    }

    @Test
    fun `guild stats come from the log`() {
        every { log.guildSummary(guildA, null, null) } returns CombatSummary(6, 4)
        val stats = service.getGuildKillStats(guildA)
        assertEquals(6, stats.totalKills)
        assertEquals(4, stats.totalDeaths)
        assertEquals(2, stats.netKills)
        assertEquals(1.5, stats.killDeathRatio)
    }

    @Test
    fun `top killers are sorted and limited`() {
        val p1 = UUID.randomUUID(); val p2 = UUID.randomUUID(); val p3 = UUID.randomUUID()
        every { log.killCounts(listOf(p1, p2, p3)) } returns mapOf(p1 to 2, p2 to 9, p3 to 5)
        assertEquals(listOf(p2 to 9, p3 to 5), service.getTopKillers(listOf(p1, p2, p3), 2).map { it.first to it.second.totalKills })
    }

    @Test
    fun `an unreadable log shows zeros instead of breaking menus`() {
        every { log.guildSummary(any(), any(), any()) } throws IllegalStateException("db down")
        assertEquals(0, service.getGuildKillStats(guildA).totalKills)
    }
}
