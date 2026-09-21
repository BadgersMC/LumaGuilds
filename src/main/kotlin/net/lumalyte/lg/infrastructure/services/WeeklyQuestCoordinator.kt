package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.config.QuestSystemConfig
import net.lumalyte.lg.domain.entities.QuestRewardTier
import net.lumalyte.lg.domain.entities.WeeklyQuestSet
import net.lumalyte.lg.domain.services.QuestGenerationHistory
import net.lumalyte.lg.domain.services.QuestGenerationSettings
import net.lumalyte.lg.domain.services.QuestGenerationValidator
import net.lumalyte.lg.domain.services.QuestGenerator
import net.lumalyte.lg.domain.services.QuestTargetCatalog
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import kotlin.random.Random

class WeeklyQuestCoordinator(
    private val questService: QuestService,
    private val configService: ProgressionConfigService,
    private val targetCatalog: QuestTargetCatalog
) {
    private val logger = LoggerFactory.getLogger(WeeklyQuestCoordinator::class.java)
    private var task: BukkitTask? = null

    fun start(plugin: Plugin) {
        refreshIfRequired(Instant.now())
        task?.cancel()
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            runCatching { refreshIfRequired(Instant.now()) }
                .onFailure { logger.warn("Weekly quest reset check failed", it) }
        }, 1_200L, 1_200L)
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    fun refreshIfRequired(now: Instant) {
        val config = configService.getProgressionConfig().quests
        if (!config.enabled) {
            questService.deactivate()
            return
        }

        val active = questService.activeQuestSet()
        val period = periodBounds(now, config)
        if (active?.weekId == period.weekId && active.endsAt.isAfter(now)) return

        val targets = targetCatalog.discoverTargets()
        if (targets.isEmpty()) {
            logger.warn("Weekly quests are enabled but no quest targets were discovered; leaving quests inactive")
            questService.deactivate()
            return
        }

        val maxHistory = maxOf(
            config.generation.exactRepeatCooldownWeeks,
            config.generation.actionTargetCooldownWeeks
        )
        val history = QuestGenerationHistory(
            questService.recentQuestSets(maxHistory).map { it.quests }
        )
        val settings = QuestGenerationSettings(
            conditionChancePercent = config.generation.conditionChancePercent,
            secondConditionChancePercent = config.generation.secondConditionChancePercent,
            maxConditions = config.generation.maxConditions,
            exactRepeatCooldownWeeks = config.generation.exactRepeatCooldownWeeks,
            actionTargetCooldownWeeks = config.generation.actionTargetCooldownWeeks,
            axisCenters = config.generation.axisCenters,
            axisWidths = config.generation.axisWidths,
            yLevels = config.generation.yLevels
        )
        val quests = QuestGenerator(
            QuestGenerationValidator(),
            Random(period.weekId.hashCode())
        ).generate(targets, config.questCount, settings, history) { tier ->
            when (tier) {
                QuestRewardTier.COMMON -> config.rewardXp.common
                QuestRewardTier.CHALLENGING -> config.rewardXp.challenging
                QuestRewardTier.HEADLINE -> config.rewardXp.headline
                QuestRewardTier.CONDITIONED -> config.rewardXp.conditioned
            }
        }

        questService.resetWeeklyQuests(
            WeeklyQuestSet(period.weekId, period.startsAt, period.endsAt, quests)
        )
    }

    private fun periodBounds(now: Instant, config: QuestSystemConfig): PeriodBounds {
        val utcNow = now.atZone(ZoneOffset.UTC)
        val resetDay = java.time.DayOfWeek.valueOf(config.resetDay.name)
        var start = utcNow.with(TemporalAdjusters.previousOrSame(resetDay))
            .withHour(config.resetHourUtc).withMinute(0).withSecond(0).withNano(0)
        if (start.isAfter(utcNow)) start = start.minusWeeks(1)
        val end = start.plusWeeks(1)
        return PeriodBounds(start.toLocalDate().toString(), start.toInstant(), end.toInstant())
    }

    private data class PeriodBounds(
        val weekId: String,
        val startsAt: Instant,
        val endsAt: Instant
    )
}
