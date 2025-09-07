package net.lumalyte.lg.application.actions.player.tool

import net.lumalyte.lg.application.errors.PlayerNotFoundException
import net.lumalyte.lg.application.results.player.tool.GivePlayerClaimToolResult
import net.lumalyte.lg.application.services.ToolItemService
import java.util.UUID

class GivePlayerClaimTool(private val toolItemService: ToolItemService) {
    fun execute(playerId: UUID): GivePlayerClaimToolResult {
        try {
            if (toolItemService.doesPlayerHaveClaimTool(playerId)) return GivePlayerClaimToolResult.PlayerAlreadyHasTool
        }
        catch (_: PlayerNotFoundException) {
            return GivePlayerClaimToolResult.PlayerNotFound
        }

        toolItemService.giveClaimTool(playerId)
        return GivePlayerClaimToolResult.Success
    }
}
