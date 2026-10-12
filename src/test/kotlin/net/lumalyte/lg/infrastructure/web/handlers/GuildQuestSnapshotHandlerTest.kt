package net.lumalyte.lg.infrastructure.web.handlers

import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.persistence.PublicQuestCapture
import net.lumalyte.lg.application.persistence.PublicQuestSnapshotReader
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.values.QuestAction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GuildQuestSnapshotHandlerTest {
    @Test fun `Long counts and public objectives are projected without claim actors`() {
        val guild = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val actor = UUID.fromString("00000000-0000-0000-0000-000000000099")
        val quest = QuestDefinition("stone", action = QuestAction.MINE_BLOCKS,
            target = QuestTarget("minecraft:block/stone", setOf(QuestAction.MINE_BLOCKS), 1, Long.MAX_VALUE),
            targetCount = Long.MAX_VALUE, tier = QuestRewardTier.COMMON, experienceReward = 5)
        val now = Instant.parse("2026-10-12T18:00:00Z")
        val week = WeeklyQuestSet("2026-W42", now, now.plusSeconds(604800), listOf(quest))
        val reader = object : PublicQuestSnapshotReader {
            override fun readPublicQuestSnapshot() = PublicQuestCapture(week,
                mapOf("stone" to listOf(GuildQuestProgress(week.weekId, "stone", guild,
                    Long.MAX_VALUE, claimed = true, claimActorId = actor, rewardDelivered = true))),
                mapOf(guild to "Guild"))
        }
        val service = mockk<QuestService>()
        every { service.fullSetBonusExperience } returns 100
        every { service.leaderboardWinnerReward(any()) } returns 50
        val snapshot = GuildQuestSnapshotHandler(reader, service).build(now)!!
        val json = Gson().toJson(snapshot)
        assertTrue(json.contains("\"9223372036854775807\""))
        assertTrue(json.contains("Mine Stone"))
        assertFalse(json.contains(actor.toString()))
        assertFalse(json.contains("claimActorId"))
        assertEquals("2026-W42", snapshot["currentWeekId"])
        assertEquals(true, snapshot["limited"])
    }
    @Test fun `missing provider is unavailable rather than fabricated quests`() {
        assertNull(GuildQuestSnapshotHandler(null, mockk()).build())
    }
}
