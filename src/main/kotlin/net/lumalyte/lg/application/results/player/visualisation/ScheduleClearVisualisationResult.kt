package net.lumalyte.lg.application.results.player.visualisation

sealed class ScheduleClearVisualisationResult {
    object Success: ScheduleClearVisualisationResult()
    object PlayerNotVisualising: ScheduleClearVisualisationResult()
    object StorageError: ScheduleClearVisualisationResult()
}
