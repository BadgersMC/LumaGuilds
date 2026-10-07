package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

data class StrikeFeedCursor(
    val occurredAt: Instant,
    val eventId: UUID,
)
