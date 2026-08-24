package net.lumalyte.lg.domain.services

import net.lumalyte.lg.domain.entities.QuestCandidate
import net.lumalyte.lg.domain.entities.QuestDefinition
import kotlin.random.Random

class QuestGenerator(
    private val validator: QuestGenerationValidator,
    private val random: Random = Random.Default,
    private val maxAttemptsPerQuest: Int = 32
) {
    init {
        require(maxAttemptsPerQuest > 0) { "Generation attempts must be positive" }
    }

    fun generate(
        candidates: List<QuestCandidate>,
        questCount: Int,
        fallback: QuestDefinition
    ): List<QuestDefinition> {
        require(questCount >= 0) { "Quest count cannot be negative" }
        require(validator.validate(fallback).isValid) { "Fallback quest must be valid" }
        if (questCount == 0) return emptyList()

        val selected = mutableListOf<QuestDefinition>()
        repeat(questCount) {
            var accepted: QuestDefinition? = null
            repeat(maxAttemptsPerQuest) {
                val roll = weightedChoice(candidates)?.definition ?: return@repeat
                if (accepted == null && roll.id !in selected.map { it.id } && validator.validate(roll).isValid) {
                    accepted = roll
                }
            }

            val usedIds = selected.mapTo(mutableSetOf()) { it.id }
            var fallbackId = fallback.id
            var suffix = 2
            while (fallbackId in usedIds) fallbackId = "${fallback.id}-${suffix++}"
            val resolved = accepted ?: fallback.copy(id = fallbackId)
            selected += resolved
        }
        return selected
    }

    private fun weightedChoice(candidates: List<QuestCandidate>): QuestCandidate? {
        if (candidates.isEmpty()) return null
        val total = candidates.sumOf { it.weight }
        var roll = random.nextInt(total)
        for (candidate in candidates) {
            roll -= candidate.weight
            if (roll < 0) return candidate
        }
        return candidates.last()
    }
}
