package net.lumalyte.lg.domain.values

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

/**
 * Tests for JoinRequirement value object.
 */
class JoinRequirementTest {

    @Test
    fun `JoinRequirement creation with valid data should succeed`() {
        // Given: Valid join requirement parameters
        val amount = 500
        val isPhysicalCurrency = true
        val currencyName = "RAW_GOLD"

        // When: Create join requirement
        val requirement = JoinRequirement(
            amount = amount,
            isPhysicalCurrency = isPhysicalCurrency,
            currencyName = currencyName
        )

        // Then: Properties should be set correctly
        assertEquals(500, requirement.amount)
        assertTrue(requirement.isPhysicalCurrency)
        assertEquals("RAW_GOLD", requirement.currencyName)
    }

    @Test
    fun `JoinRequirement with physical currency should be valid`() {
        // Given: Physical currency requirement
        val requirement = JoinRequirement(
            amount = 100,
            isPhysicalCurrency = true,
            currencyName = "RAW_GOLD"
        )

        // Then: Should be physical currency
        assertTrue(requirement.isPhysicalCurrency)
        assertEquals("RAW_GOLD", requirement.currencyName)
    }

    @Test
    fun `JoinRequirement with virtual currency should be valid`() {
        // Given: Virtual currency requirement
        val requirement = JoinRequirement(
            amount = 1000,
            isPhysicalCurrency = false,
            currencyName = "Coins"
        )

        // Then: Should be virtual currency
        assertFalse(requirement.isPhysicalCurrency)
        assertEquals("Coins", requirement.currencyName)
    }

    @Test
    fun `JoinRequirement should reject negative amount`() {
        // When/Then: Creating requirement with negative amount should fail
        val exception = assertThrows<IllegalArgumentException> {
            JoinRequirement(
                amount = -100,
                isPhysicalCurrency = true,
                currencyName = "RAW_GOLD"
            )
        }

        assertEquals("Join requirement amount must be non-negative.", exception.message)
    }

    @Test
    fun `JoinRequirement should allow zero amount`() {
        // Given: Requirement with zero amount
        val requirement = JoinRequirement(
            amount = 0,
            isPhysicalCurrency = true,
            currencyName = "RAW_GOLD"
        )

        // Then: Should be valid
        assertEquals(0, requirement.amount)
    }

    @Test
    fun `JoinRequirement should reject blank currency name`() {
        // When/Then: Creating requirement with blank currency name should fail
        val exception = assertThrows<IllegalArgumentException> {
            JoinRequirement(
                amount = 100,
                isPhysicalCurrency = true,
                currencyName = ""
            )
        }

        assertEquals("Currency name cannot be blank.", exception.message)
    }

    @Test
    fun `JoinRequirement should reject whitespace-only currency name`() {
        // When/Then: Creating requirement with whitespace-only currency name should fail
        val exception = assertThrows<IllegalArgumentException> {
            JoinRequirement(
                amount = 100,
                isPhysicalCurrency = true,
                currencyName = "   "
            )
        }

        assertEquals("Currency name cannot be blank.", exception.message)
    }

    @Test
    fun `JoinRequirement should allow currency name with spaces`() {
        // Given: Currency name with spaces
        val requirement = JoinRequirement(
            amount = 100,
            isPhysicalCurrency = false,
            currencyName = "Gold Coins"
        )

        // Then: Should be valid
        assertEquals("Gold Coins", requirement.currencyName)
    }

    @Test
    fun `JoinRequirement should allow large amount values`() {
        // Given: Very large amount
        val requirement = JoinRequirement(
            amount = 999999,
            isPhysicalCurrency = true,
            currencyName = "RAW_GOLD"
        )

        // Then: Should be valid
        assertEquals(999999, requirement.amount)
    }

    @Test
    fun `JoinRequirement equality should be based on all properties`() {
        // Given: Two requirements with same properties
        val requirement1 = JoinRequirement(
            amount = 500,
            isPhysicalCurrency = true,
            currencyName = "RAW_GOLD"
        )

        val requirement2 = JoinRequirement(
            amount = 500,
            isPhysicalCurrency = true,
            currencyName = "RAW_GOLD"
        )

        // Then: Should be equal
        assertEquals(requirement1, requirement2)
        assertEquals(requirement1.hashCode(), requirement2.hashCode())
    }

    @Test
    fun `JoinRequirement equality should differ on amount`() {
        // Given: Two requirements with different amounts
        val requirement1 = JoinRequirement(
            amount = 500,
            isPhysicalCurrency = true,
            currencyName = "RAW_GOLD"
        )

        val requirement2 = JoinRequirement(
            amount = 1000,
            isPhysicalCurrency = true,
            currencyName = "RAW_GOLD"
        )

        // Then: Should not be equal
        assertNotEquals(requirement1, requirement2)
    }

    @Test
    fun `JoinRequirement equality should differ on currency type`() {
        // Given: Two requirements with different currency types
        val requirement1 = JoinRequirement(
            amount = 500,
            isPhysicalCurrency = true,
            currencyName = "RAW_GOLD"
        )

        val requirement2 = JoinRequirement(
            amount = 500,
            isPhysicalCurrency = false,
            currencyName = "RAW_GOLD"
        )

        // Then: Should not be equal
        assertNotEquals(requirement1, requirement2)
    }

    @Test
    fun `JoinRequirement equality should differ on currency name`() {
        // Given: Two requirements with different currency names
        val requirement1 = JoinRequirement(
            amount = 500,
            isPhysicalCurrency = true,
            currencyName = "RAW_GOLD"
        )

        val requirement2 = JoinRequirement(
            amount = 500,
            isPhysicalCurrency = true,
            currencyName = "GOLD_INGOT"
        )

        // Then: Should not be equal
        assertNotEquals(requirement1, requirement2)
    }

    @Test
    fun `JoinRequirement copy should create new instance with same values`() {
        // Given: Original requirement
        val original = JoinRequirement(
            amount = 500,
            isPhysicalCurrency = true,
            currencyName = "RAW_GOLD"
        )

        // When: Copy requirement with modified amount
        val copy = original.copy(amount = 1000)

        // Then: Copy should have new amount, other properties unchanged
        assertEquals(1000, copy.amount)
        assertTrue(copy.isPhysicalCurrency)
        assertEquals("RAW_GOLD", copy.currencyName)

        // Original should be unchanged
        assertEquals(500, original.amount)
    }

    @Test
    fun `JoinRequirement toString should include all properties`() {
        // Given: Requirement
        val requirement = JoinRequirement(
            amount = 500,
            isPhysicalCurrency = true,
            currencyName = "RAW_GOLD"
        )

        // When: Convert to string
        val requirementString = requirement.toString()

        // Then: String should contain all properties
        assertTrue(requirementString.contains("amount=500"),
            "toString should include amount")
        assertTrue(requirementString.contains("isPhysicalCurrency=true"),
            "toString should include isPhysicalCurrency")
        assertTrue(requirementString.contains("currencyName=RAW_GOLD"),
            "toString should include currencyName")
    }

    @Test
    fun `JoinRequirement with common currency materials should be valid`() {
        // Test various common currency materials
        val materials = listOf(
            "RAW_GOLD",
            "GOLD_INGOT",
            "GOLD_BLOCK",
            "DIAMOND",
            "EMERALD",
            "RAW_GOLD_BLOCK"
        )

        materials.forEach { material ->
            val requirement = JoinRequirement(
                amount = 100,
                isPhysicalCurrency = true,
                currencyName = material
            )

            assertEquals(material, requirement.currencyName)
        }
    }

    @Test
    fun `JoinRequirement with common virtual currency names should be valid`() {
        // Test various common virtual currency names
        val currencyNames = listOf(
            "Coins",
            "Dollars",
            "Credits",
            "Gold",
            "Money"
        )

        currencyNames.forEach { name ->
            val requirement = JoinRequirement(
                amount = 1000,
                isPhysicalCurrency = false,
                currencyName = name
            )

            assertEquals(name, requirement.currencyName)
        }
    }
}
