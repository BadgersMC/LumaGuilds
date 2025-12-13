package net.lumalyte.lg.application.services

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for LfgJoinResult sealed class and its subtypes.
 */
class LfgJoinResultTest {

    // ===== Success Tests =====

    @Test
    fun `Success should store message`() {
        // Given: A success result
        val result = LfgJoinResult.Success("You joined Test Guild!")

        // Then: Should have correct message
        assertEquals("You joined Test Guild!", result.message)
    }

    @Test
    fun `Success should be instance of LfgJoinResult`() {
        // Given: A success result
        val result: LfgJoinResult = LfgJoinResult.Success("Joined!")

        // Then: Should be instance of LfgJoinResult
        assertTrue(result is LfgJoinResult)
        assertTrue(result is LfgJoinResult.Success)
    }

    // ===== InsufficientFunds Tests =====

    @Test
    fun `InsufficientFunds should store required and current amounts`() {
        // Given: An insufficient funds result
        val result = LfgJoinResult.InsufficientFunds(
            required = 500,
            current = 200,
            currencyType = "RAW_GOLD"
        )

        // Then: Should have correct values
        assertEquals(500, result.required)
        assertEquals(200, result.current)
        assertEquals("RAW_GOLD", result.currencyType)
    }

    @Test
    fun `InsufficientFunds should be instance of LfgJoinResult`() {
        // Given: An insufficient funds result
        val result: LfgJoinResult = LfgJoinResult.InsufficientFunds(100, 50, "Coins")

        // Then: Should be instance of LfgJoinResult
        assertTrue(result is LfgJoinResult)
        assertTrue(result is LfgJoinResult.InsufficientFunds)
    }

    @Test
    fun `InsufficientFunds should work with zero current amount`() {
        // Given: Player has no currency
        val result = LfgJoinResult.InsufficientFunds(
            required = 1000,
            current = 0,
            currencyType = "GOLD_INGOT"
        )

        // Then: Should have correct values
        assertEquals(1000, result.required)
        assertEquals(0, result.current)
    }

    // ===== GuildFull Tests =====

    @Test
    fun `GuildFull should store message`() {
        // Given: A guild full result
        val result = LfgJoinResult.GuildFull("This guild has reached maximum capacity")

        // Then: Should have correct message
        assertEquals("This guild has reached maximum capacity", result.message)
    }

    @Test
    fun `GuildFull should be instance of LfgJoinResult`() {
        // Given: A guild full result
        val result: LfgJoinResult = LfgJoinResult.GuildFull("Guild is full")

        // Then: Should be instance of LfgJoinResult
        assertTrue(result is LfgJoinResult)
        assertTrue(result is LfgJoinResult.GuildFull)
    }

    // ===== AlreadyInGuild Tests =====

    @Test
    fun `AlreadyInGuild should store message`() {
        // Given: An already in guild result
        val result = LfgJoinResult.AlreadyInGuild("You are already a member of a guild")

        // Then: Should have correct message
        assertEquals("You are already a member of a guild", result.message)
    }

    @Test
    fun `AlreadyInGuild should be instance of LfgJoinResult`() {
        // Given: An already in guild result
        val result: LfgJoinResult = LfgJoinResult.AlreadyInGuild("Already in guild")

        // Then: Should be instance of LfgJoinResult
        assertTrue(result is LfgJoinResult)
        assertTrue(result is LfgJoinResult.AlreadyInGuild)
    }

    // ===== VaultUnavailable Tests =====

    @Test
    fun `VaultUnavailable should store message`() {
        // Given: A vault unavailable result
        val result = LfgJoinResult.VaultUnavailable("Guild vault is not set up")

        // Then: Should have correct message
        assertEquals("Guild vault is not set up", result.message)
    }

    @Test
    fun `VaultUnavailable should be instance of LfgJoinResult`() {
        // Given: A vault unavailable result
        val result: LfgJoinResult = LfgJoinResult.VaultUnavailable("Vault unavailable")

        // Then: Should be instance of LfgJoinResult
        assertTrue(result is LfgJoinResult)
        assertTrue(result is LfgJoinResult.VaultUnavailable)
    }

    // ===== Error Tests =====

    @Test
    fun `Error should store message`() {
        // Given: An error result
        val result = LfgJoinResult.Error("An unexpected error occurred")

        // Then: Should have correct message
        assertEquals("An unexpected error occurred", result.message)
    }

    @Test
    fun `Error should be instance of LfgJoinResult`() {
        // Given: An error result
        val result: LfgJoinResult = LfgJoinResult.Error("Error")

        // Then: Should be instance of LfgJoinResult
        assertTrue(result is LfgJoinResult)
        assertTrue(result is LfgJoinResult.Error)
    }

    // ===== Pattern Matching Tests =====

    @Test
    fun `when expression should match all result types`() {
        // Given: Various result types
        val results = listOf(
            LfgJoinResult.Success("Joined!"),
            LfgJoinResult.InsufficientFunds(100, 50, "Coins"),
            LfgJoinResult.GuildFull("Full"),
            LfgJoinResult.AlreadyInGuild("Already member"),
            LfgJoinResult.VaultUnavailable("No vault"),
            LfgJoinResult.Error("Error")
        )

        // When/Then: All types should be matchable
        results.forEach { result ->
            val matched = when (result) {
                is LfgJoinResult.Success -> "success"
                is LfgJoinResult.InsufficientFunds -> "insufficient"
                is LfgJoinResult.GuildFull -> "full"
                is LfgJoinResult.AlreadyInGuild -> "already"
                is LfgJoinResult.VaultUnavailable -> "vault"
                is LfgJoinResult.Error -> "error"
            }
            assertNotNull(matched)
        }
    }

    // ===== Equality Tests =====

    @Test
    fun `Success with same message should be equal`() {
        val result1 = LfgJoinResult.Success("Joined!")
        val result2 = LfgJoinResult.Success("Joined!")

        assertEquals(result1, result2)
        assertEquals(result1.hashCode(), result2.hashCode())
    }

    @Test
    fun `Success with different message should not be equal`() {
        val result1 = LfgJoinResult.Success("Joined Guild A!")
        val result2 = LfgJoinResult.Success("Joined Guild B!")

        assertNotEquals(result1, result2)
    }

    @Test
    fun `InsufficientFunds with same values should be equal`() {
        val result1 = LfgJoinResult.InsufficientFunds(100, 50, "Coins")
        val result2 = LfgJoinResult.InsufficientFunds(100, 50, "Coins")

        assertEquals(result1, result2)
        assertEquals(result1.hashCode(), result2.hashCode())
    }

    @Test
    fun `InsufficientFunds with different values should not be equal`() {
        val result1 = LfgJoinResult.InsufficientFunds(100, 50, "Coins")
        val result2 = LfgJoinResult.InsufficientFunds(200, 50, "Coins")

        assertNotEquals(result1, result2)
    }

    @Test
    fun `Different result types should not be equal`() {
        val success = LfgJoinResult.Success("Joined!")
        val error = LfgJoinResult.Error("Joined!")

        assertNotEquals(success, error)
    }
}
