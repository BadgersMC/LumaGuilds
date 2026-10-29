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
    @Test fun `startup schedules retries when initial reset fails`() {
        val quests = mockk<QuestService>(relaxed = true)
        val config = mockk<ProgressionConfigService>()
        val catalog = mockk<QuestTargetCatalog>(relaxed = true)
        val plugin = mockk<org.bukkit.plugin.Plugin>()
        val server = mockk<org.bukkit.Server>()
        val scheduler = mockk<org.bukkit.scheduler.BukkitScheduler>()
        val task = mockk<org.bukkit.scheduler.BukkitTask>(relaxed = true)
        every { quests.reconcilePendingRewards() } throws IllegalStateException("unresolved payment")
        every { plugin.server } returns server
        every { server.scheduler } returns scheduler
        every { scheduler.runTaskTimer(plugin, any<Runnable>(), 1_200L, 1_200L) } returns task
        WeeklyQuestCoordinator(quests, config, catalog).start(plugin)
        verify(exactly = 1) { scheduler.runTaskTimer(plugin, any<Runnable>(), 1_200L, 1_200L) }
    }
    @Test fun `pending bank recovery defers reset`() {
        val quests = mockk<QuestService>(relaxed = true)
        val config = mockk<ProgressionConfigService>(relaxed = true)
        val catalog = mockk<QuestTargetCatalog>(relaxed = true)
        every { quests.hasPendingBankContributionRecovery() } returns true
        WeeklyQuestCoordinator(quests, config, catalog).refreshIfRequired(Instant.now())
        verify(exactly = 1) { quests.reconcilePendingRewards() }
        verify(exactly = 0) { quests.resetWeeklyQuests(any()) }
        verify(exactly = 0) { quests.deactivate() }
    }
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
