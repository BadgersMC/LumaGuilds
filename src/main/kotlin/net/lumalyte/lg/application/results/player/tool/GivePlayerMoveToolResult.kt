package net.lumalyte.lg.application.results.player.tool

sealed class GivePlayerMoveToolResult {
    object Success: GivePlayerMoveToolResult()
    object PlayerAlreadyHasTool: GivePlayerMoveToolResult()
    object ClaimNotFound: GivePlayerMoveToolResult()
    object PlayerNotFound: GivePlayerMoveToolResult()
}
