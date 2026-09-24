package net.lumalyte.lg.architecture

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

class QuestProvenanceContractTest {
    private val source = Files.readString(Path.of(
        "src/main/kotlin/net/lumalyte/lg/infrastructure/listeners/ProgressionEventListener.kt"
    ))

    @Test
    fun `all player placements are persisted before XP eligibility is checked`() {
        val placementHandler = source.substringAfter("fun onBlockPlace(event: BlockPlaceEvent)")
            .substringBefore("fun onCrafting(event: ItemCraftedEvent)")

        assertTrue(
            placementHandler.indexOf("blockProvenanceRepository.recordPlayerPlaced(position)") <
                placementHandler.indexOf("if (!eligible(event.player)) return"),
            "Block provenance must be recorded independently of guild XP eligibility"
        )
    }

    @Test
    fun `block breaks resolve and remove provenance before membership early exit`() {
        val breakHandler = source.substringAfter("fun onBlockBreak(event: BlockBreakEvent)")
            .substringBefore("fun onBlockPlace(event: BlockPlaceEvent)")

        assertTrue(
            breakHandler.contains("resolveProvenance(position, removeAfterRead = true)"),
            "Normal block breaks must remove persisted player-placement provenance"
        )
        assertTrue(
            breakHandler.indexOf("resolveProvenance(position, removeAfterRead = true)") <
                breakHandler.indexOf("if (guildIds.isNullOrEmpty()) return@resolveProvenance"),
            "Players without guild membership must still clear block provenance"
        )
    }

    @Test
    fun `Nexo targets do not assume custom blocks naturally generate`() {
        val nexoSource = Files.readString(Path.of(
            "src/main/kotlin/net/lumalyte/lg/infrastructure/services/NexoQuestTargetProvider.kt"
        ))

        assertTrue(
            !nexoSource.contains("QuestAction.MINE_BLOCKS"),
            "Custom blocks need explicit world-generation evidence before NATURAL_ONLY mining quests"
        )
    }
}
