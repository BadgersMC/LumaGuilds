package net.lumalyte.lg.infrastructure.web.handlers

import net.lumalyte.lg.application.persistence.PublicQuestSnapshotReader
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.domain.entities.QuestDefinition
import net.lumalyte.lg.domain.values.QuestAction
import net.lumalyte.lg.utils.QuestDisplayFormatter
import java.time.Instant

/** Read-only export. Historical default reward policy is deliberately not guessed. */
class GuildQuestSnapshotHandler(
    private val reader: PublicQuestSnapshotReader?,
    private val quests: QuestService,
) {
    fun build(now: Instant = Instant.now()): Map<String, Any?>? {
        val capture = reader?.readPublicQuestSnapshot() ?: return null
        val week = capture.week
        val items = if (week == null) emptyList() else listOf(mapOf(
            "id" to week.weekId, "startsAt" to week.startsAt.toString(), "endsAt" to week.endsAt.toString(),
            "fullSetBonusExperience" to quests.fullSetBonusExperience,
            "guilds" to capture.guildNames.entries.map { (id, name) ->
                mapOf("id" to id.toString(), "name" to name, "banner" to null)
            },
            "quests" to week.quests.map { quest ->
                val rows = capture.progress[quest.id].orEmpty()
                    .sortedWith(compareByDescending<net.lumalyte.lg.domain.entities.GuildQuestProgress> { it.currentCount }
                        .thenBy { it.guildId.toString() })
                objective(quest) + mapOf("standings" to rows.mapIndexed { index, progress -> mapOf(
                    "guildId" to progress.guildId.toString(), "rank" to index + 1,
                    "count" to progress.currentCount.toString(), "claimed" to progress.claimed,
                    "rewardDelivered" to progress.rewardDelivered,
                ) })
            },
        ))
        return mapOf("kind" to "guildquests", "schemaVersion" to 1,
            "generatedAt" to now.toString(), "currentWeekId" to week?.weekId,
            "limited" to true, "items" to items)
    }

    private fun objective(quest: QuestDefinition): Map<String, Any?> {
        val payouts = quest.leaderboardPayouts.ifEmpty {
            quests.leaderboardWinnerReward(quest).takeIf { it > 0 }?.let { mapOf(1 to it) }.orEmpty()
        }
        return mapOf(
            "id" to quest.id, "name" to QuestDisplayFormatter.name(quest),
            "description" to QuestDisplayFormatter.description(quest), "action" to quest.action.name,
            "tier" to quest.tier.name, "icon" to icon(quest.action),
            "targetCount" to quest.targetCount.toString(), "experienceReward" to quest.experienceReward,
            "conditions" to quest.conditions.map(QuestDisplayFormatter::condition),
            "provenance" to quest.target.provenancePolicy.name,
            "itemRewards" to quest.itemRewards.map { reward -> mapOf(
                "item" to reward.itemId, "name" to QuestDisplayFormatter.token(reward.itemId), "amount" to reward.amount,
            ) }, "leaderboard" to quest.leaderboard,
            "payouts" to payouts.entries.sortedBy { it.key }.map { (rank, xp) -> mapOf("rank" to rank, "experience" to xp) },
        )
    }
    private fun icon(action: QuestAction): String = when (action) {
        QuestAction.KILL_PLAYERS, QuestAction.KILL_MOBS -> "iron_sword"
        QuestAction.HARVEST_CROPS -> "iron_hoe"
        QuestAction.MINE_BLOCKS -> "iron_pickaxe"
        QuestAction.PLACE_BLOCKS -> "bricks"
        QuestAction.CRAFT_ITEMS -> "crafting_table"
        QuestAction.SMELT_ITEMS -> "furnace"
        QuestAction.FISH -> "fishing_rod"
        QuestAction.ENCHANT_ITEMS -> "enchanting_table"
        QuestAction.DEPOSIT_BANK -> "gold_ingot"
        QuestAction.WIN_WARS -> "nether_star"
    }
}
