package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.VisualisationPerformanceService
import net.lumalyte.lg.application.services.PlayerVisualisationStats
import net.lumalyte.lg.application.services.OverallVisualisationStats
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Bukkit implementation of VisualisationPerformanceService that tracks
 * visualization performance metrics for optimization purposes.
 */
class VisualisationPerformanceServiceBukkit : VisualisationPerformanceService {
    
    private val logger = LoggerFactory.getLogger(VisualisationPerformanceServiceBukkit::class.java)
    
    // Performance thresholds
    private val maxOperationTime = Duration.ofMillis(100) // 100ms threshold
    private val maxAreasPerOperation = 100 // Maximum areas per operation
    private val maxPositionsPerOperation = 1000 // Maximum positions per operation
    
    // Tracking data structures
    private val activeOperations = ConcurrentHashMap<String, OperationData>()
    private val playerStats = ConcurrentHashMap<UUID, PlayerStatsData>()
    private val overallStats = OverallStatsData()
    
    override fun startOperation(playerId: UUID, operationType: String): String {
        val operationId = "${playerId}_${System.currentTimeMillis()}_${operationType}"
        val startTime = Instant.now()
        
        activeOperations[operationId] = OperationData(
            playerId = playerId,
            operationType = operationType,
            startTime = startTime
        )
        
        // Initialize player stats if not exists
        playerStats.computeIfAbsent(playerId) { PlayerStatsData() }
        
        logger.debug("Started visualization operation: $operationId for player: $playerId")
        return operationId
    }
    
    override fun completeOperation(operationId: String, success: Boolean, areasCount: Int, positionsCount: Int) {
        val operationData = activeOperations.remove(operationId) ?: return
        val endTime = Instant.now()
        val duration = Duration.between(operationData.startTime, endTime)
        
        // Update player stats
        val playerData = playerStats[operationData.playerId]
        if (playerData != null) {
            playerData.totalOperations.incrementAndGet()
            if (success) {
                playerData.successfulOperations.incrementAndGet()
            } else {
                playerData.failedOperations.incrementAndGet()
            }
            
            playerData.totalAreasVisualized.addAndGet(areasCount.toLong())
            playerData.totalPositionsVisualized.addAndGet(positionsCount.toLong())
            playerData.lastOperationTime = endTime
            
            // Update average operation time
            val currentTotal = playerData.totalOperationTime.addAndGet(duration.toNanos())
            val totalOps = playerData.totalOperations.get()
            playerData.averageOperationTime = Duration.ofNanos(currentTotal / totalOps)
        }
        
        // Update overall stats
        overallStats.totalOperations.incrementAndGet()
        overallStats.totalAreasVisualized.addAndGet(areasCount.toLong())
        overallStats.totalPositionsVisualized.addAndGet(positionsCount.toLong())
        
        // Log performance warnings
        if (duration > maxOperationTime) {
            logger.warn("Slow visualization operation detected: $operationId took ${duration.toMillis()}ms")
        }
        
        if (areasCount > maxAreasPerOperation) {
            logger.warn("Large area count detected: $operationId has $areasCount areas")
        }
        
        if (positionsCount > maxPositionsPerOperation) {
            logger.warn("Large position count detected: $operationId has $positionsCount positions")
        }
        
        logger.debug("Completed visualization operation: $operationId in ${duration.toMillis()}ms")
    }
    
    override fun getPlayerStats(playerId: UUID): PlayerVisualisationStats {
        val playerData = playerStats[playerId] ?: return PlayerVisualisationStats(
            playerId = playerId,
            totalOperations = 0,
            successfulOperations = 0,
            failedOperations = 0,
            averageOperationTime = Duration.ZERO,
            totalAreasVisualized = 0,
            totalPositionsVisualized = 0,
            lastOperationTime = null,
            performanceIssues = emptyList()
        )
        
        val performanceIssues = mutableListOf<String>()
        
        // Check for performance issues
        if (playerData.averageOperationTime > maxOperationTime) {
            performanceIssues.add("Average operation time (${playerData.averageOperationTime.toMillis()}ms) exceeds threshold (${maxOperationTime.toMillis()}ms)")
        }
        
        if (playerData.totalOperations.get() > 1000) {
            performanceIssues.add("High operation count: ${playerData.totalOperations.get()}")
        }
        
        return PlayerVisualisationStats(
            playerId = playerId,
            totalOperations = playerData.totalOperations.get(),
            successfulOperations = playerData.successfulOperations.get(),
            failedOperations = playerData.failedOperations.get(),
            averageOperationTime = playerData.averageOperationTime,
            totalAreasVisualized = playerData.totalAreasVisualized.get(),
            totalPositionsVisualized = playerData.totalPositionsVisualized.get(),
            lastOperationTime = playerData.lastOperationTime,
            performanceIssues = performanceIssues
        )
    }
    
    override fun getOverallStats(): OverallVisualisationStats {
        val totalOps = overallStats.totalOperations.get()
        val averageTime = if (totalOps > 0) {
            Duration.ofNanos(overallStats.totalOperationTime.get() / totalOps)
        } else {
            Duration.ZERO
        }
        
        val performanceWarnings = mutableListOf<String>()
        
        // Check for overall performance issues
        if (averageTime > maxOperationTime) {
            performanceWarnings.add("Overall average operation time (${averageTime.toMillis()}ms) exceeds threshold (${maxOperationTime.toMillis()}ms)")
        }
        
        if (overallStats.totalAreasVisualized.get() > 10000) {
            performanceWarnings.add("High total areas visualized: ${overallStats.totalAreasVisualized.get()}")
        }
        
        if (overallStats.totalPositionsVisualized.get() > 100000) {
            performanceWarnings.add("High total positions visualized: ${overallStats.totalPositionsVisualized.get()}")
        }
        
        return OverallVisualisationStats(
            totalOperations = totalOps,
            averageOperationTime = averageTime,
            totalAreasVisualized = overallStats.totalAreasVisualized.get(),
            totalPositionsVisualized = overallStats.totalPositionsVisualized.get(),
            activePlayers = playerStats.size,
            performanceWarnings = performanceWarnings
        )
    }
    
    override fun isPerformanceAcceptable(playerId: UUID): Boolean {
        val stats = getPlayerStats(playerId)
        return stats.performanceIssues.isEmpty()
    }
    
    override fun getOptimizationRecommendations(playerId: UUID): List<String> {
        val recommendations = mutableListOf<String>()
        val stats = getPlayerStats(playerId)
        
        if (stats.averageOperationTime > maxOperationTime) {
            recommendations.add("Consider reducing visualization complexity or using async operations")
        }
        
        if (stats.totalAreasVisualized > 1000) {
            recommendations.add("Consider implementing area culling or reducing view distance")
        }
        
        if (stats.totalPositionsVisualized > 10000) {
            recommendations.add("Consider implementing position culling or reducing detail level")
        }
        
        if (stats.totalOperations > 1000) {
            recommendations.add("Consider implementing operation rate limiting")
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("Performance is within acceptable limits")
        }
        
        return recommendations
    }
    
    // Internal data classes for tracking
    private data class OperationData(
        val playerId: UUID,
        val operationType: String,
        val startTime: Instant
    )
    
    private class PlayerStatsData {
        val totalOperations = AtomicLong(0)
        val successfulOperations = AtomicLong(0)
        val failedOperations = AtomicLong(0)
        val totalAreasVisualized = AtomicLong(0)
        val totalPositionsVisualized = AtomicLong(0)
        val totalOperationTime = AtomicLong(0)
        var averageOperationTime = Duration.ZERO
        var lastOperationTime: Instant? = null
    }
    
    private class OverallStatsData {
        val totalOperations = AtomicLong(0)
        val totalAreasVisualized = AtomicLong(0)
        val totalPositionsVisualized = AtomicLong(0)
        val totalOperationTime = AtomicLong(0)
    }
}
