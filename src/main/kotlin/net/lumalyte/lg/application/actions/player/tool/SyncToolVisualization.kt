package net.lumalyte.lg.application.actions.player.tool

import net.lumalyte.lg.application.actions.player.visualisation.DisplayVisualisation
import net.lumalyte.lg.application.actions.player.visualisation.IsPlayerVisualising
import net.lumalyte.lg.application.actions.player.visualisation.ScheduleClearVisualisation
import net.lumalyte.lg.application.results.player.visualisation.IsPlayerVisualisingResult
import net.lumalyte.lg.application.services.ToolItemService
import net.lumalyte.lg.domain.values.Position3D
import java.util.UUID

class SyncToolVisualization(private val toolItemService: ToolItemService,
                                   private val displayVisualisation: DisplayVisualisation,
                                   private val scheduleClearVisualisation: ScheduleClearVisualisation,
                                   private val isPlayerVisualising: IsPlayerVisualising) {
    fun execute(playerId: UUID, position: Position3D,
                mainHandItemData: Map<String, String>?, offHandItemData: Map<String, String>?) {
        // Check if player is holding tool based on the item they're holding
        val holdingClaimTool = (toolItemService.isClaimTool(mainHandItemData)
                || (toolItemService.isClaimTool(offHandItemData)))

        // Visualise or unvisualise depending on if player is holding claim tool
        if (holdingClaimTool) {
            displayVisualisation.execute(playerId, position)
        } else {
            val result = isPlayerVisualising.execute(playerId)
            if (result is IsPlayerVisualisingResult.Success && result.isVisualising) {
                scheduleClearVisualisation.execute(playerId)
            }
        }
    }
}
