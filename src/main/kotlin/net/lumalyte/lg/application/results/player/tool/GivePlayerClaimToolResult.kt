package net.lumalyte.lg.application.results.player.tool

sealed class GivePlayerClaimToolResult {
    object Success: GivePlayerClaimToolResult()
    object PlayerAlreadyHasTool: GivePlayerClaimToolResult()
    object PlayerNotFound: GivePlayerClaimToolResult()
}
