package net.lumalyte.lg.domain.services

import net.lumalyte.lg.domain.entities.QuestTarget
import net.lumalyte.lg.domain.values.QuestAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QuestTargetCatalogTest {
    @Test
    fun `catalog sorts targets and removes duplicate provider entries`() {
        val zombie = target("minecraft:entity/zombie", QuestAction.KILL_MOBS)
        val stone = target("minecraft:block/stone", QuestAction.MINE_BLOCKS)
        val duplicateStone = stone.copy()

        val catalog = QuestTargetCatalog(
            listOf(
                provider("second", listOf(zombie, duplicateStone)),
                provider("first", listOf(stone))
            )
        )

        assertEquals(
            listOf("minecraft:entity/zombie", "minecraft:block/stone"),
            catalog.discoverTargets().map { it.id }
        )
    }

    private fun target(id: String, action: QuestAction) = QuestTarget(
        id = id,
        allowedActions = setOf(action),
        minimumAmount = 1,
        maximumAmount = 100
    )

    private fun provider(
        namespace: String,
        targets: List<QuestTarget>
    ) = object : QuestTargetProvider {
        override val namespace: String = namespace
        override fun discoverTargets(): Collection<QuestTarget> = targets
    }
}
