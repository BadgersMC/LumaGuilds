package net.lumalyte.lg.application.actions.player.tool

import net.lumalyte.lg.application.errors.PlayerNotFoundException
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.results.player.tool.GivePlayerMoveToolResult
import net.lumalyte.lg.application.services.ToolItemService
import java.util.UUID

class GivePlayerMoveTool(private val claimRepository: ClaimRepository, private val toolItemService: ToolItemService) {
    fun execute(playerId: UUID, claimId: UUID): GivePlayerMoveToolResult {
        val claim = claimRepository.getById(claimId) ?: return GivePlayerMoveToolResult.ClaimNotFound

        try {
            if (toolItemService.doesPlayerHaveMoveTool(playerId, claim))
                return GivePlayerMoveToolResult.PlayerAlreadyHasTool
        }
        catch (_: PlayerNotFoundException) {
            return GivePlayerMoveToolResult.PlayerNotFound
        }

        toolItemService.giveMoveTool(playerId, claim)
        return GivePlayerMoveToolResult.Success
    }
}
