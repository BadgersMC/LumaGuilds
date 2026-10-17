package net.lumalyte.lg.config

data class QuestGenerationConfig(
    val conditionChancePercent: Int = 45,
    val secondConditionChancePercent: Int = 15,
    val maxConditions: Int = 2,
    val exactRepeatCooldownWeeks: Int = 8,
    val actionTargetCooldownWeeks: Int = 3,
    val axisCenters: List<Int> = listOf(0, 0, 0, 0, 500, -500, 1000, -1000, 2500, -2500),
    val axisWidths: List<Int> = listOf(50, 100, 250, 500),
    val yLevels: List<Int> = listOf(-32, 0, 32, 64, 96, 128)
)
