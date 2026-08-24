package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.config.QuestDefinitionConfig
import net.lumalyte.lg.config.QuestSystemConfig
import net.lumalyte.lg.domain.entities.BlockProvenancePolicy
import net.lumalyte.lg.domain.entities.QuestCandidate
import net.lumalyte.lg.domain.entities.QuestCondition
import net.lumalyte.lg.domain.entities.QuestConditionType
import net.lumalyte.lg.domain.entities.QuestDefinition
import net.lumalyte.lg.domain.entities.QuestRewardTier
import net.lumalyte.lg.domain.entities.QuestTarget
import net.lumalyte.lg.domain.entities.WeeklyQuestSet
import net.lumalyte.lg.domain.services.QuestGenerationValidator
import net.lumalyte.lg.domain.services.QuestGenerator
import net.lumalyte.lg.domain.values.QuestAction
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import kotlin.random.Random

class WeeklyQuestCoordinator(
    private val questService: QuestService,
    private val configService: ProgressionConfigService
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
        if (!config.enabled) return
        val expected = periodFor(now, config)
        val active = questService.activeQuestSet()
        if (active?.weekId == expected.weekId && active.endsAt.isAfter(now)) return
        questService.resetWeeklyQuests(expected)
    }

    private fun periodFor(now: Instant, config: QuestSystemConfig): WeeklyQuestSet {
        val utcNow = now.atZone(ZoneOffset.UTC)
        val resetDay = java.time.DayOfWeek.valueOf(config.resetDay.name)
        var start = utcNow.with(TemporalAdjusters.previousOrSame(resetDay))
            .withHour(config.resetHourUtc).withMinute(0).withSecond(0).withNano(0)
        if (start.isAfter(utcNow)) start = start.minusWeeks(1)
        val end = start.plusWeeks(1)
        val weekId = start.toLocalDate().toString()
        val candidates = config.definitions.mapNotNull { toCandidate(it, config) }
        val validator = QuestGenerationValidator()
        val valid = candidates.filter { validator.validate(it.definition).isValid }
        val quests = if (valid.isEmpty()) emptyList() else {
            val fallback = valid.first().definition
            QuestGenerator(validator, Random(weekId.hashCode())).generate(
                valid,
                config.questCount.coerceAtMost(valid.size),
                fallback
            )
        }
        return WeeklyQuestSet(weekId, start.toInstant(), end.toInstant(), quests)
    }

    private fun toCandidate(raw: QuestDefinitionConfig, config: QuestSystemConfig): QuestCandidate? = runCatching {
        val action = QuestAction.valueOf(raw.action)
        val tier = QuestRewardTier.valueOf(raw.tier)
        val conditionType = raw.conditionType?.let(QuestConditionType::valueOf)
        val condition = conditionType?.let { QuestCondition(it, raw.conditionValue) }
        val target = QuestTarget(
            id = raw.target,
            allowedActions = setOf(action),
            minimumAmount = raw.minimumAmount,
            maximumAmount = raw.maximumAmount,
            naturalDimensions = raw.naturalDimensions,
            naturalBiomes = raw.naturalBiomes,
            supportedConditions = conditionType?.let(::setOf) ?: emptySet(),
            provenancePolicy = BlockProvenancePolicy.valueOf(raw.provenancePolicy)
        )
        QuestCandidate(
            QuestDefinition(
                id = raw.id, nameKey = raw.nameKey, descriptionKey = raw.descriptionKey,
                action = action, target = target, targetCount = raw.amount, tier = tier, condition = condition,
                experienceReward = when (tier) {
                    QuestRewardTier.COMMON -> config.rewardXp.common
                    QuestRewardTier.CHALLENGING -> config.rewardXp.challenging
                    QuestRewardTier.HEADLINE -> config.rewardXp.headline
                    QuestRewardTier.CONDITIONED -> config.rewardXp.conditioned
                },
                leaderboard = raw.leaderboard,
                leaderboardPayouts = raw.leaderboardPayouts
            ),
            raw.weight
        )
    }.onFailure { logger.warn("Ignoring invalid weekly quest definition ${raw.id}: ${it.message}") }.getOrNull()
}
