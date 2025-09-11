package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

/**
 * Represents a peace agreement between two guilds to end a war.
 */
data class PeaceAgreement(
    val id: UUID = UUID.randomUUID(),
    val warId: UUID, // The war this agreement is for
    val proposingGuildId: UUID, // Guild that proposed the agreement
    val targetGuildId: UUID, // Guild that needs to accept the agreement
    val proposedAt: Instant = Instant.now(),
    val expiresAt: Instant = Instant.now().plusSeconds(86400), // 24 hours to accept
    val peaceTerms: String, // Description of the peace terms
    val offering: PeaceOffering? = null, // Optional item/resource offering
    val accepted: Boolean = false,
    val rejected: Boolean = false,
    val acceptedAt: Instant? = null
) {
    /**
     * Checks if the peace agreement is still valid.
     */
    val isValid: Boolean
        get() = !accepted && !rejected && Instant.now().isBefore(expiresAt)

    /**
     * Gets the remaining time before expiration.
     */
    val remainingTime: Long
        get() = if (Instant.now().isBefore(expiresAt)) {
            expiresAt.epochSecond - Instant.now().epochSecond
        } else {
            0L
        }
}

/**
 * Represents an offering as part of a peace agreement.
 */
data class PeaceOffering(
    val id: UUID = UUID.randomUUID(),
    val items: List<PeaceOfferingItem> = emptyList(),
    val money: Int = 0,
    val exp: Int = 0
) {
    /**
     * Gets the total value of the offering for display purposes.
     */
    val totalValue: String
        get() {
            val parts = mutableListOf<String>()
            if (money > 0) parts.add("${money} coins")
            if (exp > 0) parts.add("${exp} EXP")
            if (items.isNotEmpty()) parts.add("${items.size} items")
            return parts.joinToString(", ")
        }

    /**
     * Checks if the offering has any value.
     */
    val hasValue: Boolean
        get() = money > 0 || exp > 0 || items.isNotEmpty()
}

/**
 * Represents a specific item in a peace offering.
 */
data class PeaceOfferingItem(
    val material: String,
    val amount: Int,
    val displayName: String? = null,
    val lore: List<String> = emptyList()
)
