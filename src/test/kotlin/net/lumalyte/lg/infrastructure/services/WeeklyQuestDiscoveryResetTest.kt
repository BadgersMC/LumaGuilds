package net.lumalyte.lg.infrastructure.services

import io.mockk.*
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.config.ProgressionSystemConfig
import net.lumalyte.lg.config.QuestSystemConfig
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.services.QuestTargetCatalog
import net.lumalyte.lg.domain.values.QuestAction
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class WeeklyQuestDiscoveryResetTest {
    @Test fun `new set announces once and minute refresh does not repeat it`() {
        val quests = mockk<QuestService>(relaxed = true)
        val config = mockk<ProgressionConfigService>()
        val catalog = mockk<QuestTargetCatalog>()
        var active: WeeklyQuestSet? = null
        every { config.getProgressionConfig() } returns ProgressionSystemConfig(quests = QuestSystemConfig(questCount = 1))
        every { quests.activeQuestSet() } answers { active }
        every { quests.recentQuestSets(any()) } returns emptyList()
        every { catalog.discoverTargets() } returns listOf(QuestTarget("minecraft:block/stone", setOf(QuestAction.MINE_BLOCKS), 1, 100))
        every { quests.resetWeeklyQuests(any()) } answers { active = firstArg(); Unit }
        var announcements = 0
        val coordinator = WeeklyQuestCoordinator(quests, config, catalog) { announcements++ }
        val now = Instant.parse("2026-10-11T12:00:00Z")
        coordinator.refreshIfRequired(now)
        coordinator.refreshIfRequired(now.plusSeconds(60))
        assertEquals(1, announcements)
        verify(exactly = 1) { quests.resetWeeklyQuests(any()) }
    }

    @Test fun `disabled quests never announce a new set`() {
        val quests = mockk<QuestService>(relaxed = true)
        val config = mockk<ProgressionConfigService>()
        val catalog = mockk<QuestTargetCatalog>()
        every { config.getProgressionConfig() } returns ProgressionSystemConfig(quests = QuestSystemConfig(enabled = false))
        var announcements = 0
        WeeklyQuestCoordinator(quests, config, catalog) { announcements++ }.refreshIfRequired(Instant.now())
        assertEquals(0, announcements)
        verify(exactly = 0) { catalog.discoverTargets() }
    }
}
