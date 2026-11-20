package net.lumalyte.lg.domain.values

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

/** REQ-199 and REQ-200. */
class KillRecordingTest {
    private val killer = UUID.randomUUID()
    private val victim = UUID.randomUUID()
    private val guildA = UUID.randomUUID()
    private val guildB = UUID.randomUUID()
    private val now = Instant.parse("2026-11-10T10:00:00Z")

    @Test
    fun `kills between guilds, or with one guild involved, are recorded`() {
        assertEquals(KillDecision.RECORD, KillRecording.decide(killer, victim, guildA, guildB, null, now))
        assertEquals(KillDecision.RECORD, KillRecording.decide(killer, victim, guildA, null, null, now))
        assertEquals(KillDecision.RECORD, KillRecording.decide(killer, victim, null, guildB, null, now))
    }

    @Test
    fun `self, guildless, same-guild and farmed kills are skipped`() {
        assertEquals(KillDecision.SELF, KillRecording.decide(killer, killer, guildA, guildA, null, now))
        assertEquals(KillDecision.NO_GUILD, KillRecording.decide(killer, victim, null, null, null, now))
        assertEquals(KillDecision.SAME_GUILD, KillRecording.decide(killer, victim, guildA, guildA, null, now))
        assertEquals(KillDecision.FARMING, KillRecording.decide(killer, victim, guildA, guildB, now.minus(Duration.ofMinutes(9)), now))
        assertEquals(KillDecision.RECORD, KillRecording.decide(killer, victim, guildA, guildB, now.minus(Duration.ofMinutes(10)), now))
    }

    @Test
    fun `ratio is kills over deaths, or kills with no deaths`() {
        assertEquals(1.5, CombatSummary(3, 2).ratio)
        assertEquals(4.0, CombatSummary(4, 0).ratio)
        assertEquals(0.0, CombatSummary(0, 0).ratio)
    }
}
