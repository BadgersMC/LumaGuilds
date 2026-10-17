package net.lumalyte.lg.utils

import net.lumalyte.lg.domain.entities.QuestCondition
import net.lumalyte.lg.domain.entities.QuestConditionType
import net.lumalyte.lg.domain.entities.QuestDefinition
import net.lumalyte.lg.domain.entities.QuestTarget
import net.lumalyte.lg.domain.values.QuestAction
import java.text.NumberFormat
import java.time.Duration
import java.util.Locale

object QuestDisplayFormatter {
    fun token(value: String): String =
        value.lowercase()
            .replace('-', '_')
            .split('_')
            .filter(String::isNotBlank)
            .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    fun target(id: String): String = token(id.substringAfterLast('/'))

    fun name(quest: QuestDefinition): String = when {
        quest.action == QuestAction.FISH && quest.target.value.equals("any", ignoreCase = true) -> "Catch Any Fish"
        quest.action == QuestAction.DEPOSIT_BANK -> "Deposit Gold Ore"
        quest.action == QuestAction.WIN_WARS -> "Win Wars"
        quest.action == QuestAction.KILL_PLAYERS -> "Kill Players"
        else -> "${actionVerb(quest.action)} ${target(quest.target.id)}"
    }

    fun description(quest: QuestDefinition): String {
        val amount = NumberFormat.getIntegerInstance(Locale.US).format(quest.targetCount)
        val objective = when {
            quest.action == QuestAction.FISH && quest.target.value.equals("any", ignoreCase = true) ->
                "Catch $amount fish"
            quest.action == QuestAction.DEPOSIT_BANK ->
                "Deposit $amount Gold Ore to the Guild Bank"
            quest.action == QuestAction.WIN_WARS ->
                "Win $amount ${if (quest.targetCount == 1L) "war" else "wars"}"
            quest.action == QuestAction.KILL_PLAYERS ->
                "Kill $amount ${if (quest.targetCount == 1L) "player" else "players"}"
            else ->
                "${actionVerb(quest.action)} $amount ${countedTarget(quest.target, quest.targetCount, quest.action)}"
        }
        val rendered = conditions(quest.conditions)
        return if (rendered.isBlank()) objective else "$objective $rendered"
    }

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

    private fun countedTarget(target: QuestTarget, count: Long, action: QuestAction): String {
        val label = target(target.id)
        if (count == 1L) return label
        return when (action) {
            QuestAction.CRAFT_ITEMS, QuestAction.SMELT_ITEMS, QuestAction.ENCHANT_ITEMS, QuestAction.KILL_MOBS ->
                pluralize(label)
            else -> label
        }
    }

    private fun pluralize(label: String): String {
        val split = label.lastIndexOf(' ')
        val prefix = if (split >= 0) label.substring(0, split + 1) else ""
        val word = if (split >= 0) label.substring(split + 1) else label
        val lower = word.lowercase()
        val plural = when (lower) {
            "sheep" -> word
            "fish" -> word
            "person" -> "Players"
            else -> when {
                lower.endsWith("s") -> word
                lower.endsWith("ch") || lower.endsWith("sh") || lower.endsWith("x") || lower.endsWith("z") -> "${word}es"
                lower.endsWith("y") && word.length > 1 && word[word.length - 2].lowercaseChar() !in "aeiou" ->
                    word.dropLast(1) + "ies"
                else -> word + "s"
            }
        }
        return prefix + plural
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
