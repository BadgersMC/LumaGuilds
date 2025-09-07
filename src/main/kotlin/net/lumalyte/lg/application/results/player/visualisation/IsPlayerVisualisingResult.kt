package net.lumalyte.lg.application.results.player.visualisation

sealed class IsPlayerVisualisingResult {
    data class Success(val isVisualising: Boolean): IsPlayerVisualisingResult()
    object StorageError: IsPlayerVisualisingResult()
}
