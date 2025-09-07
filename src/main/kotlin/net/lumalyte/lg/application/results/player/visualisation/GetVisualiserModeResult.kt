package net.lumalyte.lg.application.results.player.visualisation

sealed class GetVisualiserModeResult {
    data class Success(val visualiserMode: Int): GetVisualiserModeResult()
    object StorageError: GetVisualiserModeResult()
}
