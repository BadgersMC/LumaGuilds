package net.lumalyte.lg.application.actions.player.tool

import net.lumalyte.lg.application.results.player.tool.GetClaimIdFromMoveToolResult
import net.lumalyte.lg.application.services.ToolItemService
import java.util.UUID

class GetClaimIdFromMoveTool(private val toolItemService: ToolItemService) {
    fun execute(itemData: Map<String, String>?): GetClaimIdFromMoveToolResult {
        val claimIdString = toolItemService.getClaimIdFromPlayerMoveTool(itemData)
        if (claimIdString != null) {
            val claimId = UUID.fromString(claimIdString)
            return GetClaimIdFromMoveToolResult.Success(claimId)
        }
        return GetClaimIdFromMoveToolResult.NotMoveTool
    }
}
