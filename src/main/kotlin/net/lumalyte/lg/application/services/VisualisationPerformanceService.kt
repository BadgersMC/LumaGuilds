package net.lumalyte.lg.application.services

import java.util.UUID
import java.time.Instant
import java.time.Duration

/**
 * Service for monitoring visualization performance and providing metrics for optimization.
 * This service tracks performance metrics to help identify bottlenecks and optimize
 * visualization operations.
 */
interface VisualisationPerformanceService {
    
    /**
     * Record the start time of a visualization operation.
     * 
     * @param playerId The ID of the player performing the visualization
     * @param operationType The type of visualization operation
     * @return A unique operation ID for tracking
     */
    fun startOperation(playerId: UUID, operationType: String): String
    
    /**
     * Record the completion of a visualization operation.
     * 
     * @param operationId The operation ID returned from startOperation
     * @param success Whether the operation completed successfully
     * @param areasCount The number of areas visualized
     * @param positionsCount The number of positions visualized
     */
    fun completeOperation(operationId: String, success: Boolean, areasCount: Int, positionsCount: Int)
    
    /**
     * Get performance statistics for a specific player.
     * 
     * @param playerId The ID of the player
     * @return Performance statistics for the player
     */
    fun getPlayerStats(playerId: UUID): PlayerVisualisationStats
    
    /**
     * Get overall performance statistics.
     * 
     * @return Overall performance statistics
     */
    fun getOverallStats(): OverallVisualisationStats
    
    /**
     * Check if visualization performance is within acceptable limits.
     * 
     * @param playerId The ID of the player to check
     * @return true if performance is acceptable, false if there are performance issues
     */
    fun isPerformanceAcceptable(playerId: UUID): Boolean
    
    /**
     * Get recommendations for performance optimization.
     * 
     * @param playerId The ID of the player
     * @return List of optimization recommendations
     */
    fun getOptimizationRecommendations(playerId: UUID): List<String>
}

/**
 * Performance statistics for a specific player.
 */
data class PlayerVisualisationStats(
    val playerId: UUID,
    val totalOperations: Long,
    val successfulOperations: Long,
    val failedOperations: Long,
    val averageOperationTime: Duration,
    val totalAreasVisualized: Long,
    val totalPositionsVisualized: Long,
    val lastOperationTime: Instant?,
    val performanceIssues: List<String>
)

/**
 * Overall performance statistics across all players.
 */
data class OverallVisualisationStats(
    val totalOperations: Long,
    val averageOperationTime: Duration,
    val totalAreasVisualized: Long,
    val totalPositionsVisualized: Long,
    val activePlayers: Int,
    val performanceWarnings: List<String>
)
