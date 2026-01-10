package net.lumalyte.lg.domain.events

import net.lumalyte.lg.domain.entities.Guild
import java.util.UUID

/**
 * Event fired when a guild is successfully created.
 * This event allows various systems to react to guild creation without tight coupling.
 */
data class GuildCreatedEvent(
    val guild: Guild,
    val ownerId: UUID
)
