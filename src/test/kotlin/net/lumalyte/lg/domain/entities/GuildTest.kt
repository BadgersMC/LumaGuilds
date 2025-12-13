package net.lumalyte.lg.domain.entities

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

/**
 * Tests for Guild entity including join fee properties.
 */
class GuildTest {

    @Test
    fun `Guild construction with join fee properties should succeed`() {
        // Given: Valid guild parameters with join fee
        val guildId = UUID.randomUUID()
        val createdAt = Instant.now()

        // When: Create guild with join fee enabled and amount
        val guild = Guild(
            id = guildId,
            name = "Test Guild",
            createdAt = createdAt,
            joinFeeEnabled = true,
            joinFeeAmount = 500
        )

        // Then: Guild should be created with correct properties
        assertEquals(guildId, guild.id)
        assertEquals("Test Guild", guild.name)
        assertEquals(createdAt, guild.createdAt)
        assertTrue(guild.joinFeeEnabled)
        assertEquals(500, guild.joinFeeAmount)
    }

    @Test
    fun `Guild should have default values for join fee properties`() {
        // Given: Minimal guild parameters without join fee
        val guildId = UUID.randomUUID()
        val createdAt = Instant.now()

        // When: Create guild without specifying join fee properties
        val guild = Guild(
            id = guildId,
            name = "Test Guild",
            createdAt = createdAt
        )

        // Then: Join fee properties should have default values
        assertFalse(guild.joinFeeEnabled, "joinFeeEnabled should default to false")
        assertEquals(0, guild.joinFeeAmount, "joinFeeAmount should default to 0")
    }

    @Test
    fun `Guild with joinFeeEnabled false should be valid`() {
        // Given: Guild with join fee explicitly disabled
        val guild = Guild(
            id = UUID.randomUUID(),
            name = "Free Guild",
            createdAt = Instant.now(),
            joinFeeEnabled = false,
            joinFeeAmount = 0
        )

        // Then: Guild should be valid
        assertFalse(guild.joinFeeEnabled)
        assertEquals(0, guild.joinFeeAmount)
    }

    @Test
    fun `Guild with joinFeeEnabled true and positive amount should be valid`() {
        // Given: Guild with join fee enabled and positive amount
        val guild = Guild(
            id = UUID.randomUUID(),
            name = "Premium Guild",
            createdAt = Instant.now(),
            joinFeeEnabled = true,
            joinFeeAmount = 1000
        )

        // Then: Guild should be valid
        assertTrue(guild.joinFeeEnabled)
        assertEquals(1000, guild.joinFeeAmount)
    }

    @Test
    fun `Guild should reject negative joinFeeAmount`() {
        // When/Then: Creating guild with negative join fee amount should fail
        val exception = assertThrows<IllegalArgumentException> {
            Guild(
                id = UUID.randomUUID(),
                name = "Invalid Guild",
                createdAt = Instant.now(),
                joinFeeEnabled = true,
                joinFeeAmount = -100
            )
        }

        assertEquals("Join fee amount cannot be negative.", exception.message)
    }

    @Test
    fun `Guild should allow joinFeeAmount of zero`() {
        // Given: Guild with join fee enabled but amount is zero
        val guild = Guild(
            id = UUID.randomUUID(),
            name = "Zero Fee Guild",
            createdAt = Instant.now(),
            joinFeeEnabled = true,
            joinFeeAmount = 0
        )

        // Then: Guild should be valid
        assertTrue(guild.joinFeeEnabled)
        assertEquals(0, guild.joinFeeAmount)
    }

    @Test
    fun `Guild should allow large joinFeeAmount values`() {
        // Given: Guild with very large join fee
        val guild = Guild(
            id = UUID.randomUUID(),
            name = "Expensive Guild",
            createdAt = Instant.now(),
            joinFeeEnabled = true,
            joinFeeAmount = 999999
        )

        // Then: Guild should be valid
        assertTrue(guild.joinFeeEnabled)
        assertEquals(999999, guild.joinFeeAmount)
    }

    @Test
    fun `Guild copy should preserve join fee properties`() {
        // Given: Original guild with join fee
        val original = Guild(
            id = UUID.randomUUID(),
            name = "Original Guild",
            createdAt = Instant.now(),
            joinFeeEnabled = true,
            joinFeeAmount = 750
        )

        // When: Copy guild with modified name
        val copy = original.copy(name = "Copied Guild")

        // Then: Join fee properties should be preserved
        assertEquals("Copied Guild", copy.name)
        assertTrue(copy.joinFeeEnabled)
        assertEquals(750, copy.joinFeeAmount)
    }

    @Test
    fun `Guild copy should allow modifying joinFeeEnabled`() {
        // Given: Original guild with join fee enabled
        val original = Guild(
            id = UUID.randomUUID(),
            name = "Test Guild",
            createdAt = Instant.now(),
            joinFeeEnabled = true,
            joinFeeAmount = 500
        )

        // When: Copy guild with join fee disabled
        val copy = original.copy(joinFeeEnabled = false)

        // Then: Join fee should be disabled in copy, enabled in original
        assertTrue(original.joinFeeEnabled)
        assertFalse(copy.joinFeeEnabled)
        assertEquals(500, copy.joinFeeAmount) // Amount should be preserved
    }

    @Test
    fun `Guild copy should allow modifying joinFeeAmount`() {
        // Given: Original guild with join fee
        val original = Guild(
            id = UUID.randomUUID(),
            name = "Test Guild",
            createdAt = Instant.now(),
            joinFeeEnabled = true,
            joinFeeAmount = 500
        )

        // When: Copy guild with different fee amount
        val copy = original.copy(joinFeeAmount = 1000)

        // Then: Fee amount should be updated in copy, unchanged in original
        assertEquals(500, original.joinFeeAmount)
        assertEquals(1000, copy.joinFeeAmount)
        assertTrue(copy.joinFeeEnabled)
    }

    @Test
    fun `Guild with both isOpen and joinFeeEnabled should be valid`() {
        // Given: Guild that is open with join requirements
        val guild = Guild(
            id = UUID.randomUUID(),
            name = "Open With Fee Guild",
            createdAt = Instant.now(),
            isOpen = true,
            joinFeeEnabled = true,
            joinFeeAmount = 250
        )

        // Then: Both properties should be set correctly
        assertTrue(guild.isOpen)
        assertTrue(guild.joinFeeEnabled)
        assertEquals(250, guild.joinFeeAmount)
    }

    @Test
    fun `Guild with isOpen true but joinFeeEnabled false should be valid`() {
        // Given: Guild that is open without join requirements
        val guild = Guild(
            id = UUID.randomUUID(),
            name = "Free Open Guild",
            createdAt = Instant.now(),
            isOpen = true,
            joinFeeEnabled = false,
            joinFeeAmount = 0
        )

        // Then: Guild should be open but free to join
        assertTrue(guild.isOpen)
        assertFalse(guild.joinFeeEnabled)
        assertEquals(0, guild.joinFeeAmount)
    }

    @Test
    fun `Guild should maintain all existing validations with join fee properties`() {
        // Test that adding join fee properties doesn't break existing validations

        // Name validation
        assertThrows<IllegalArgumentException> {
            Guild(
                id = UUID.randomUUID(),
                name = "", // Empty name
                createdAt = Instant.now(),
                joinFeeEnabled = true,
                joinFeeAmount = 100
            )
        }

        // Level validation
        assertThrows<IllegalArgumentException> {
            Guild(
                id = UUID.randomUUID(),
                name = "Test Guild",
                level = 0, // Invalid level
                createdAt = Instant.now(),
                joinFeeEnabled = true,
                joinFeeAmount = 100
            )
        }

        // Bank balance validation
        assertThrows<IllegalArgumentException> {
            Guild(
                id = UUID.randomUUID(),
                name = "Test Guild",
                bankBalance = -1, // Negative balance
                createdAt = Instant.now(),
                joinFeeEnabled = true,
                joinFeeAmount = 100
            )
        }
    }

    @Test
    fun `Guild equality should consider join fee properties`() {
        // Given: Two guilds with same properties except join fee
        val id = UUID.randomUUID()
        val createdAt = Instant.now()

        val guild1 = Guild(
            id = id,
            name = "Test Guild",
            createdAt = createdAt,
            joinFeeEnabled = true,
            joinFeeAmount = 500
        )

        val guild2 = Guild(
            id = id,
            name = "Test Guild",
            createdAt = createdAt,
            joinFeeEnabled = false,
            joinFeeAmount = 0
        )

        // Then: Guilds should not be equal due to different join fee settings
        assertNotEquals(guild1, guild2)
    }

    @Test
    fun `Guild toString should include join fee properties`() {
        // Given: Guild with join fee
        val guild = Guild(
            id = UUID.randomUUID(),
            name = "Test Guild",
            createdAt = Instant.now(),
            joinFeeEnabled = true,
            joinFeeAmount = 500
        )

        // When: Convert to string
        val guildString = guild.toString()

        // Then: String should contain join fee information
        assertTrue(guildString.contains("joinFeeEnabled=true"),
            "toString should include joinFeeEnabled")
        assertTrue(guildString.contains("joinFeeAmount=500"),
            "toString should include joinFeeAmount")
    }
}
