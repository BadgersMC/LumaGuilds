package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

/**
 * Represents a member of a guild.
 *
 * @property playerId The unique identifier of the player.
 * @property guildId The unique identifier of the guild this member belongs to.
 * @property rankId The unique identifier of the rank assigned to this member.
 * @property joinedAt The timestamp when the member joined the guild.
 */
data class Member(
    val playerId: UUID,
    val guildId: UUID,
    val rankId: UUID,
    val joinedAt: Instant
)