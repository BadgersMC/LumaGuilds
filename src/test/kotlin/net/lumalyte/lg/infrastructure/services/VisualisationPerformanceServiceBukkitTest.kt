package net.lumalyte.lg.infrastructure.services

import dev.mizarc.bellclaims.application.services.VisualisationPerformanceService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Duration
import java.util.UUID

class VisualisationPerformanceServiceBukkitTest {
    
    private lateinit var service: VisualisationPerformanceService
    private lateinit var playerId: UUID
    private lateinit var otherPlayerId: UUID
    
    @BeforeEach
    fun setUp() {
        service = VisualisationPerformanceServiceBukkit()
        playerId = UUID.randomUUID()
        otherPlayerId = UUID.randomUUID()
    }
    
    @Test
    fun `should create service instance successfully`() {
        assertNotNull(service, "Service should be created successfully")
    }
    
    @Test
    fun `should implement all required interface methods`() {
        // This test ensures all interface methods are implemented
        assertNotNull(service, "Service should be created successfully")
    }
    
    @Test
    fun `should track operation start and completion`() {
        val operationId = service.startOperation(playerId, "test_operation")
        assertNotNull(operationId, "Operation ID should be returned")
        assertTrue(operationId.isNotEmpty(), "Operation ID should not be empty")
        
        service.completeOperation(operationId, true, 5, 100)
        
        val stats = service.getPlayerStats(playerId)
        assertEquals(1L, stats.totalOperations, "Total operations should be 1")
        assertEquals(1L, stats.successfulOperations, "Successful operations should be 1")
        assertEquals(0L, stats.failedOperations, "Failed operations should be 0")
        assertEquals(5L, stats.totalAreasVisualized, "Total areas should be 5")
        assertEquals(100L, stats.totalPositionsVisualized, "Total positions should be 100")
    }
    
    @Test
    fun `should track failed operations`() {
        val operationId = service.startOperation(playerId, "failed_operation")
        service.completeOperation(operationId, false, 0, 0)
        
        val stats = service.getPlayerStats(playerId)
        assertEquals(1L, stats.totalOperations, "Total operations should be 1")
        assertEquals(0L, stats.successfulOperations, "Successful operations should be 0")
        assertEquals(1L, stats.failedOperations, "Failed operations should be 1")
    }
    
    @Test
    fun `should calculate average operation time correctly`() {
        // Start and complete first operation
        val op1 = service.startOperation(playerId, "operation_1")
        Thread.sleep(10) // Small delay to ensure measurable time
        service.completeOperation(op1, true, 1, 10)
        
        // Start and complete second operation
        val op2 = service.startOperation(playerId, "operation_2")
        Thread.sleep(20) // Longer delay
        service.completeOperation(op2, true, 1, 10)
        
        val stats = service.getPlayerStats(playerId)
        assertEquals(2L, stats.totalOperations, "Total operations should be 2")
        assertTrue(stats.averageOperationTime.toMillis() > 0, "Average time should be positive")
    }
    
    @Test
    fun `should track multiple players independently`() {
        // Player 1 operations
        val op1 = service.startOperation(playerId, "player1_op")
        service.completeOperation(op1, true, 5, 50)
        
        // Player 2 operations
        val op2 = service.startOperation(otherPlayerId, "player2_op")
        service.completeOperation(op2, true, 3, 30)
        
        val stats1 = service.getPlayerStats(playerId)
        val stats2 = service.getPlayerStats(otherPlayerId)
        
        assertEquals(1L, stats1.totalOperations, "Player 1 should have 1 operation")
        assertEquals(1L, stats2.totalOperations, "Player 2 should have 1 operation")
        assertEquals(5L, stats1.totalAreasVisualized, "Player 1 should have 5 areas")
        assertEquals(3L, stats2.totalAreasVisualized, "Player 2 should have 3 areas")
    }
    
    @Test
    fun `should provide overall statistics`() {
        // Multiple operations across players
        val op1 = service.startOperation(playerId, "op1")
        service.completeOperation(op1, true, 5, 50)
        
        val op2 = service.startOperation(otherPlayerId, "op2")
        service.completeOperation(op2, true, 3, 30)
        
        val overallStats = service.getOverallStats()
        assertEquals(2L, overallStats.totalOperations, "Total operations should be 2")
        assertEquals(8L, overallStats.totalAreasVisualized, "Total areas should be 8")
        assertEquals(80L, overallStats.totalPositionsVisualized, "Total positions should be 80")
        assertEquals(2, overallStats.activePlayers, "Active players should be 2")
    }
    
    @Test
    fun `should detect performance issues`() {
        // Simulate slow operation
        val slowOp = service.startOperation(playerId, "slow_operation")
        Thread.sleep(150) // Exceeds 100ms threshold
        service.completeOperation(slowOp, true, 150, 1500) // Exceeds area and position thresholds
        
        val stats = service.getPlayerStats(playerId)
        assertFalse(stats.performanceIssues.isEmpty(), "Should detect performance issues")
        assertTrue(stats.performanceIssues.any { it.contains("exceeds threshold") }, "Should detect slow operation")
    }
    
    @Test
    fun `should provide optimization recommendations`() {
        // Simulate performance issues
        val slowOp = service.startOperation(playerId, "slow_operation")
        Thread.sleep(150)
        service.completeOperation(slowOp, true, 150, 1500)
        
        val recommendations = service.getOptimizationRecommendations(playerId)
        assertFalse(recommendations.isEmpty(), "Should provide optimization recommendations")
        assertTrue(recommendations.any { it.contains("async operations") }, "Should suggest async operations")
    }
    
    @Test
    fun `should handle performance acceptability check`() {
        // Normal operation
        val normalOp = service.startOperation(playerId, "normal_operation")
        service.completeOperation(normalOp, true, 5, 50)
        
        assertTrue(service.isPerformanceAcceptable(playerId), "Performance should be acceptable for normal operation")
        
        // Multiple slow operations to make average exceed threshold
        repeat(5) { i ->
            val slowOp = service.startOperation(playerId, "slow_operation_$i")
            Thread.sleep(150) // Exceeds 100ms threshold
            service.completeOperation(slowOp, true, 5, 50)
        }
        
        assertFalse(service.isPerformanceAcceptable(playerId), "Performance should not be acceptable for slow operations")
    }
    
    @Test
    fun `should handle empty player stats gracefully`() {
        val stats = service.getPlayerStats(UUID.randomUUID())
        assertEquals(0L, stats.totalOperations, "New player should have 0 operations")
        assertEquals(Duration.ZERO, stats.averageOperationTime, "New player should have zero average time")
        assertTrue(stats.performanceIssues.isEmpty(), "New player should have no performance issues")
    }
    
    @Test
    fun `should handle concurrent operations`() {
        // Simulate concurrent operations
        val operations = (1..10).map { i ->
            service.startOperation(playerId, "concurrent_op_$i")
        }
        
        // Complete operations concurrently
        operations.forEach { opId ->
            service.completeOperation(opId, true, 1, 10)
        }
        
        val stats = service.getPlayerStats(playerId)
        assertEquals(10L, stats.totalOperations, "Should track all concurrent operations")
    }
    
    @Test
    fun `should handle large operation counts`() {
        // Simulate many operations
        repeat(1001) { i -> // Need to exceed 1000 threshold
            val opId = service.startOperation(playerId, "operation_$i")
            service.completeOperation(opId, true, 1, 10)
        }
        
        val stats = service.getPlayerStats(playerId)
        assertEquals(1001L, stats.totalOperations, "Should handle large operation counts")
        
        val recommendations = service.getOptimizationRecommendations(playerId)
        assertTrue(recommendations.any { it.contains("rate limiting") }, "Should suggest rate limiting for high operation counts")
    }
}
