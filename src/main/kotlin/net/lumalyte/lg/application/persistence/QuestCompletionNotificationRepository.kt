package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.QuestCompletionNotification
import java.util.UUID

interface QuestCompletionNotificationRepository {
    /** Persists once; returns false when the deterministic notification id already exists. */
    fun add(notification: QuestCompletionNotification): Boolean

    /** Returns undelivered quest-completion notifications oldest-first. */
    fun getPending(playerId: UUID): List<QuestCompletionNotification>

    /** Marks a pending completion notification delivered. */
    fun markDelivered(notificationId: UUID, deliveredAt: Long): Boolean
}
