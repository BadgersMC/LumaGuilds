package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

/**
 * Represents a player's preferred active party for chat.
 *
 * @property playerId The ID of the player.
 * @property partyId The ID of the player's preferred active party.
 * @property setAt The timestamp when this preference was set.
 */
data class PlayerPartyPreference(
    val playerId: UUID,
    val partyId: UUID,
    val setAt: Instant = Instant.now()
)
