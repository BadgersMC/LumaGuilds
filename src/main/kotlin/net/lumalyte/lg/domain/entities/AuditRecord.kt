package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

/**
 * Represents an audit record for tracking important actions in the system.
 *
 * @property id The unique identifier for the audit record.
 * @property time The timestamp when the action occurred.
 * @property actorId The unique identifier of the player who performed the action.
 * @property guildId The unique identifier of the guild involved in the action, if any.
 * @property action The type of action that was performed.
 * @property details Additional details about the action in JSON format.
 */
data class AuditRecord(
    val id: UUID,
    val time: Instant,
    val actorId: UUID,
    val guildId: UUID? = null,
    val action: String,
    val details: String? = null
) {
    init {
        require(action.isNotBlank()) { "Action cannot be blank." }
    }
}
