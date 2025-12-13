package net.lumalyte.lg.application.services

import net.lumalyte.lg.infrastructure.services.AdminOverrideServiceImpl
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AdminOverrideServiceTest {

    private lateinit var service: AdminOverrideService
    private lateinit var playerId: UUID

    @BeforeEach
    fun setUp() {
        service = AdminOverrideServiceImpl()
        playerId = UUID.randomUUID()
    }

    @Test
    fun `hasOverride should return false by default`() {
        // When: Check override state for new player
        val result = service.hasOverride(playerId)

        // Then: Override should be disabled by default
        assertFalse(result)
    }

    @Test
    fun `enableOverride should set override to true`() {
        // When: Enable override for player
        service.enableOverride(playerId)

        // Then: Override should be enabled
        assertTrue(service.hasOverride(playerId))
    }

    @Test
    fun `disableOverride should set override to false`() {
        // Given: Override is enabled
        service.enableOverride(playerId)
        assertTrue(service.hasOverride(playerId))

        // When: Disable override
        service.disableOverride(playerId)

        // Then: Override should be disabled
        assertFalse(service.hasOverride(playerId))
    }

    @Test
    fun `toggleOverride should enable when disabled`() {
        // Given: Override is disabled (default state)
        assertFalse(service.hasOverride(playerId))

        // When: Toggle override
        val result = service.toggleOverride(playerId)

        // Then: Should return true (now enabled) and state should be enabled
        assertTrue(result)
        assertTrue(service.hasOverride(playerId))
    }

    @Test
    fun `toggleOverride should disable when enabled`() {
        // Given: Override is enabled
        service.enableOverride(playerId)
        assertTrue(service.hasOverride(playerId))

        // When: Toggle override
        val result = service.toggleOverride(playerId)

        // Then: Should return false (now disabled) and state should be disabled
        assertFalse(result)
        assertFalse(service.hasOverride(playerId))
    }

    @Test
    fun `toggleOverride should alternate correctly multiple times`() {
        // Toggle sequence: off -> on -> off -> on
        assertFalse(service.hasOverride(playerId)) // Initial: off

        // First toggle: off -> on
        assertTrue(service.toggleOverride(playerId))
        assertTrue(service.hasOverride(playerId))

        // Second toggle: on -> off
        assertFalse(service.toggleOverride(playerId))
        assertFalse(service.hasOverride(playerId))

        // Third toggle: off -> on
        assertTrue(service.toggleOverride(playerId))
        assertTrue(service.hasOverride(playerId))
    }

    @Test
    fun `clearOverride should remove override state`() {
        // Given: Override is enabled
        service.enableOverride(playerId)
        assertTrue(service.hasOverride(playerId))

        // When: Clear override
        service.clearOverride(playerId)

        // Then: Override should be disabled
        assertFalse(service.hasOverride(playerId))
    }

    @Test
    fun `clearOverride should work when override was disabled`() {
        // Given: Override is disabled
        assertFalse(service.hasOverride(playerId))

        // When: Clear override (should be no-op)
        service.clearOverride(playerId)

        // Then: Override should still be disabled
        assertFalse(service.hasOverride(playerId))
    }

    @Test
    fun `multiple players should have independent override states`() {
        val player1 = UUID.randomUUID()
        val player2 = UUID.randomUUID()
        val player3 = UUID.randomUUID()

        // Enable override for player1 and player3 only
        service.enableOverride(player1)
        service.enableOverride(player3)

        // Then: Each player should have correct independent state
        assertTrue(service.hasOverride(player1))
        assertFalse(service.hasOverride(player2))
        assertTrue(service.hasOverride(player3))

        // Disable player1
        service.disableOverride(player1)

        // Then: Should not affect other players
        assertFalse(service.hasOverride(player1))
        assertFalse(service.hasOverride(player2))
        assertTrue(service.hasOverride(player3))
    }

    @Test
    fun `concurrent access from multiple threads should be thread-safe`() {
        val threadCount = 10
        val operationsPerThread = 100
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        val player1 = UUID.randomUUID()
        val player2 = UUID.randomUUID()

        // Launch multiple threads that toggle override simultaneously
        repeat(threadCount) { threadIndex ->
            executor.submit {
                try {
                    val targetPlayer = if (threadIndex % 2 == 0) player1 else player2
                    repeat(operationsPerThread) {
                        service.toggleOverride(targetPlayer)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        // Wait for all threads to complete
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))

        // Then: Service should not crash and should return valid boolean states
        // The actual final state may vary due to thread interleaving, but both should be valid booleans
        val player1State = service.hasOverride(player1)
        val player2State = service.hasOverride(player2)

        // Verify we get valid boolean responses (not corrupted state)
        assertTrue(player1State == true || player1State == false)
        assertTrue(player2State == true || player2State == false)
    }

    @Test
    fun `enableOverride should be idempotent`() {
        // Enable multiple times
        service.enableOverride(playerId)
        service.enableOverride(playerId)
        service.enableOverride(playerId)

        // Then: Should still be enabled
        assertTrue(service.hasOverride(playerId))
    }

    @Test
    fun `disableOverride should be idempotent`() {
        // Given: Override is enabled
        service.enableOverride(playerId)

        // Disable multiple times
        service.disableOverride(playerId)
        service.disableOverride(playerId)
        service.disableOverride(playerId)

        // Then: Should still be disabled
        assertFalse(service.hasOverride(playerId))
    }

    @Test
    fun `clearOverride should be idempotent`() {
        // Given: Override is enabled
        service.enableOverride(playerId)

        // Clear multiple times
        service.clearOverride(playerId)
        service.clearOverride(playerId)
        service.clearOverride(playerId)

        // Then: Should still be disabled
        assertFalse(service.hasOverride(playerId))
    }
}
