package net.lumalyte.lg.application.results.player.visualisation

import net.lumalyte.lg.domain.values.Position3D

sealed class GetVisualisedClaimBlocksResult {
    data class Success(val blockPositions: Set<Position3D>): GetVisualisedClaimBlocksResult()
    object NotVisualising: GetVisualisedClaimBlocksResult()
    object ClaimNotFound: GetVisualisedClaimBlocksResult()
    object StorageError: GetVisualisedClaimBlocksResult()
}
