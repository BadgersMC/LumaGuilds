package net.lumalyte.lg.utils

import net.lumalyte.lg.domain.entities.QuestCondition
import net.lumalyte.lg.domain.entities.QuestConditionType
import net.lumalyte.lg.domain.entities.QuestDefinition
import net.lumalyte.lg.domain.values.QuestAction
import java.time.Duration

object QuestDisplayFormatter {
    fun token(value: String): String =
        value.lowercase()
            .replace('-', '_')
            .split('_')
            .filter(String::isNotBlank)
            .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    fun target(id: String): String = token(id.substringAfterLast('/'))

    fun name(quest: QuestDefinition): String =
        "${actionVerb(quest.action)} ${target(quest.target.id)}"

    private fun actionVerb(action: QuestAction): String = when (action) {
        QuestAction.KILL_PLAYERS, QuestAction.KILL_MOBS -> "Kill"
        QuestAction.HARVEST_CROPS -> "Harvest"
        QuestAction.MINE_BLOCKS -> "Mine"
        QuestAction.PLACE_BLOCKS -> "Place"
        QuestAction.CRAFT_ITEMS -> "Craft"
        QuestAction.SMELT_ITEMS -> "Smelt"
        QuestAction.FISH -> "Catch"
        QuestAction.ENCHANT_ITEMS -> "Enchant"
        QuestAction.DEPOSIT_BANK -> "Deposit"
        QuestAction.WIN_WARS -> "Win"
    }
    fun description(quest: QuestDefinition): String = buildString {
        append(quest.targetCount).append(' ').append(target(quest.target.id))
        val rendered = conditions(quest.conditions)
        if (rendered.isNotBlank()) append(' ').append(rendered)
    }

    fun conditions(values: List<QuestCondition>): String =
        values.joinToString(" and ", transform = ::condition)

    fun condition(value: QuestCondition): String = when (value.type) {
        QuestConditionType.X_WITHIN -> axisCondition("X", value.value)
        QuestConditionType.Z_WITHIN -> axisCondition("Z", value.value)
        QuestConditionType.ABOVE_Y -> "above Y=${value.value.orEmpty()}"
        QuestConditionType.BELOW_Y -> "below Y=${value.value.orEmpty()}"
        QuestConditionType.IN_DIMENSION -> "in ${token(value.value.orEmpty())}"
        QuestConditionType.IN_BIOME -> "in ${token(value.value.orEmpty())}"
        QuestConditionType.WITH_TOOL -> "using ${token(value.value.orEmpty())}"
        QuestConditionType.WITHOUT_TOOL -> "without ${token(value.value.orEmpty())}"
        QuestConditionType.USING_TRANSPORT -> "using ${token(value.value.orEmpty())}"
        QuestConditionType.WITHOUT_ELYTRA -> "without Elytra"
    }

    fun duration(value: Duration): String {
        val days = value.toDays()
        val hours = value.minusDays(days).toHours()
        val minutes = value.minusDays(days).minusHours(hours).toMinutes()
        return "${days}d ${hours}h ${minutes}m"
    }

    private fun axisCondition(axis: String, encoded: String?): String {
        val parts = encoded.orEmpty().split(':', limit = 2)
        val center = parts.getOrNull(0) ?: "?"
        val radius = parts.getOrNull(1) ?: "?"
        return "within $radius blocks of $axis=$center"
    }
}
