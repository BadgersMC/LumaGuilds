package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

/**
 * Represents a pending guild invitation.
 *
 * @property guildId The ID of the guild the player is invited to.
 * @property guildName The name of the guild (cached for display purposes).
 * @property invitedPlayerId The ID of the player being invited.
 * @property inviterPlayerId The ID of the player who sent the invitation.
 * @property inviterName The name of the inviter (cached for display purposes).
 * @property timestamp When the invitation was created.
 */
data class GuildInvitation(
    val guildId: UUID,
    val guildName: String,
    val invitedPlayerId: UUID,
    val inviterPlayerId: UUID,
    val inviterName: String,
    val timestamp: Instant = Instant.now()
)
